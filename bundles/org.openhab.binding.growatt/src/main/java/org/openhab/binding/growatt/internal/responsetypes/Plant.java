package org.openhab.binding.growatt.internal.responsetypes;

public class Plant extends GrowattObject {
    public String plantMoneyText;
    public String plantName;
    public int plantId;
    public boolean isHaveStorage;
    public String todayEnergy;
    public String totalEnergy;
    public String currentPower;

    @Override
    public String getId() {
        return "" + plantId;
    }
}
