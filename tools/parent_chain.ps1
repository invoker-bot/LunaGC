param([int]$Start = 42172, [int]$Depth = 8)
$id = $Start
for ($i = 0; $i -lt $Depth; $i++) {
    $q = Get-CimInstance Win32_Process -Filter "ProcessId=$id" -ErrorAction SilentlyContinue
    if (-not $q) { break }
    "{0} -> {1}  (parent {2})" -f $id, $q.Name, $q.ParentProcessId
    $id = $q.ParentProcessId
}
