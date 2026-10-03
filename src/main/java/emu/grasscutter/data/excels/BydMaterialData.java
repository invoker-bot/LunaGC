package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/** Beyond materials have their own use actions; they must not enter the Teyvat ItemData parser. */
@Getter
@ResourceType(name = "BydMaterialExcelConfigData.json")
public class BydMaterialData extends GameResource {
    private int id;
    private long nameTextMapHash;
    private String bydMaterialType;
    private List<Use> itemUse = List.of();

    @Getter
    public static class Use {
        private String useOp;
        private List<String> useParam = List.of();
    }
}
