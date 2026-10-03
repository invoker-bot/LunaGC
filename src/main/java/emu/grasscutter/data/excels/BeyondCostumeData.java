package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.Getter;

@Getter
@ResourceType(name = "BeyondCostumeExcelConfigData.json")
public class BeyondCostumeData extends GameResource {
    private int costumeId;
    private int suitId;

    @Override
    public int getId() {
        return costumeId;
    }
}
