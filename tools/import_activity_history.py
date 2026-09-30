"""Build the dated activity catalogue from BWIKI facts and the selected client resources.

Only names, dates, version numbers and source links are imported. Gameplay comes
from the client resource tables/scripts and server implementations.
Run: python tools/import_activity_history.py --resources resources
"""

import argparse
import datetime as dt
import json
import pathlib
import re
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[1]
WIKI_API = "https://wiki.biligame.com/ys/api.php"
TYPE_SOURCE = "https://raw.githubusercontent.com/Genshin-PS-Archive/WeedwackerPS/main/src/GameServer/Data/Enums/NewActivityType.cs"
# Shared titles have multiple client records, including beta/cancelled variants.
# These map the first published run, not all subsequent reruns, to its resource ID.
FIRST_RUN_IDS = {
    "原素烘炉": 5001,
    "百货奇货": 5003,
    "海灯节": 2002,
    "秘宝迷踪": 5011,
    "百人一揆": 5040,
    "风行迷踪": 5023,
}
TITLE_IDS = {
    "佳肴尚温": 5006,
    "飞行挑战": 5007,
    "白垩与黑龙": 3001,
    "风花的邀约": 2003,
    "导能原盘绪论": 2004,
}


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "LunaGC activity catalogue importer"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read().decode("utf-8")


def normalized(name):
    return re.sub(r"[\W_]", "", name).replace("活动", "")


def timestamp(value):
    match = re.fullmatch(r"(\d{4})/(\d\d)/(\d\d) (\d\d):(\d\d)(?::(\d\d))?", value.strip())
    if not match:
        date_only = re.match(r"(\d{4})/(\d\d)/(\d\d) .*(?:更新后|版本)", value.strip())
        if date_only:
            return dt.date(*map(int, date_only.groups())).isoformat()
        return None
    return dt.datetime(*[int(part or 0) for part in match.groups()], tzinfo=dt.timezone(dt.timedelta(hours=8))).isoformat()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resources", type=pathlib.Path, default=ROOT / "resources")
    parser.add_argument("--types", type=pathlib.Path, help="Local snapshot of NewActivityType.cs")
    args = parser.parse_args()
    resource_rows = json.loads((args.resources / "ExcelBinOutput/NewActivityExcelConfigData.json").read_text("utf-8"))
    names = json.loads((args.resources / "TextMap/TextMapCHS.json").read_text("utf-8"))
    type_text = args.types.read_text("utf-8-sig") if args.types else fetch(TYPE_SOURCE)
    type_ids = {name: int(value) for name, value in re.findall(r"(NEW_ACTIVITY_\w+)\s*=\s*(\d+)", type_text)}
    server_types = (ROOT / "src/main/java/emu/grasscutter/game/props/ActivityType.java").read_text("utf-8")
    type_ids.update({name: int(value) for name, value in re.findall(r"(NEW_ACTIVITY_\w+)\((\d+)\)", server_types)})
    by_name = {}
    for row in resource_rows:
        name = names.get(str(row.get("nameTextMapHash")), "")
        if name:
            by_name.setdefault(normalized(name), []).append(row)

    wiki_rows = []
    offset = 0
    while True:
        query = f"[[分类:活动]]|?名称|?开始描述|?结束描述|?所属版本|?类别|limit=500|offset={offset}"
        response = json.loads(fetch(WIKI_API + "?" + urllib.parse.urlencode({"action": "ask", "query": query, "format": "json"})))
        wiki_rows.extend(response["query"]["results"].values())
        next_offset = response.get("query-continue-offset")
        if next_offset is None or next_offset <= offset:
            break
        offset = next_offset

    entries = []
    unmatched = []
    for row in wiki_rows:
        props = row["printouts"]
        name = next(iter(props.get("名称", [])), row["fulltext"])
        candidates = by_name.get(normalized(name), [])
        if normalized(name) in TITLE_IDS:
            candidates = [item for item in resource_rows if item["activityId"] == TITLE_IDS[normalized(name)]]
        if len(candidates) > 1:
            first_id = FIRST_RUN_IDS.get(normalized(name))
            candidates = [item for item in candidates if item["activityId"] == first_id]
        begin = timestamp(next(iter(props.get("开始描述", [])), ""))
        end = timestamp(next(iter(props.get("结束描述", [])), ""))
        if not candidates or not begin or not end:
            unmatched.append({"name": name, "sourceUrl": row["fullurl"], "reason": "no resource match" if not candidates else "non-fixed date"})
            continue
        for candidate in candidates:
            activity_id = candidate["activityId"]
            entries.append({
                "key": f"{activity_id}-{begin[:10]}",
                "activityId": activity_id,
                "activityType": type_ids.get(candidate["activityType"], 0),
                "typeName": candidate["activityType"],
                "name": name,
                "version": next(iter(props.get("所属版本", [])), ""),
                "officialBeginTime": begin,
                "officialEndTime": end,
                "officialBeginDescription": next(iter(props.get("开始描述", [])), ""),
                "officialEndDescription": next(iter(props.get("结束描述", [])), ""),
                "sourceUrl": row["fullurl"],
                "scheduleId": activity_id * 1000 + 1,
            })
    unique = {entry["key"]: entry for entry in entries}
    entries = sorted(unique.values(), key=lambda entry: (entry["officialBeginTime"], entry["activityId"]))
    dated_count = len(entries)
    dated_ids = {entry["activityId"] for entry in entries}
    for row in resource_rows:
        activity_id = row["activityId"]
        if activity_id not in dated_ids:
            entries.append({
                "key": f"resource-{activity_id}", "activityId": activity_id,
                "activityType": type_ids.get(row["activityType"], 0), "typeName": row["activityType"],
                "name": names.get(str(row.get("nameTextMapHash")), f"活动 #{activity_id}"),
                "version": "", "officialBeginTime": "", "officialEndTime": "", "sourceUrl": "",
                "scheduleId": activity_id * 1000 + 1,
            })
    target = ROOT / "data/ActivityHistory.json"
    target.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", "utf-8")
    fallback = ROOT / "src/main/resources/defaults/data/ActivityHistory.json"
    fallback.write_bytes(target.read_bytes())
    report = ROOT / "local/activity-history-import-report.json"
    report.parent.mkdir(exist_ok=True)
    report.write_text(json.dumps(unmatched, ensure_ascii=False, indent=2), "utf-8")
    print(f"Imported {dated_count} dated sessions; catalogue covers {len(entries)} resource activities; {len(unmatched)} records need review.")
    print("First activities:")
    for entry in entries[:12]:
        print(entry["officialBeginTime"], entry["activityId"], entry["name"])


if __name__ == "__main__":
    main()
