package org.openhab.binding.growatt.internal.responsetypes;

import java.util.List;

public class PlantList extends GenericMessage {
    public List<Plant> data;
    public TotalData totalData;
}
