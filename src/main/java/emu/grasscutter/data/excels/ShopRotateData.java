package emu.grasscutter.data.excels;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

@Getter
@ResourceType(name = "ShopRotateExcelConfigData.json")
public class ShopRotateData extends GameResource {
  private int id;
  private int rotateId;
  private int itemId;
  private int rotateOrder;
}
