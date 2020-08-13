package org.openhab.binding.growatt.internal.responsetypes;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.unit.MetricPrefix;
import org.eclipse.smarthome.core.library.unit.SIUnits;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParserHelper {
    private final static Logger logger = LoggerFactory.getLogger(ParserHelper.class);

    public static List<QuantityType<?>> getDataValues(Object dataObject, String[] fieldNames)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        List<QuantityType<?>> result = new ArrayList<QuantityType<?>>();
        @NonNull
        QuantityType<?> dataValue = new QuantityType<>();

        for (String fieldName : fieldNames) {
            // Check if the data Object has the field
            Field field = dataObject.getClass().getDeclaredField(fieldName);
            Type fieldType = field.getGenericType();
            logger.trace("Field {} is of type {}", field, fieldType);
            switch (fieldType.toString()) {
                case "class java.lang.String":
                    String[] dataStrings = ((String) field.get(dataObject)).split("\\s+");
                    if (dataStrings.length != 2) {
                        throw new NoSuchFieldException("Response data does not consist of 2 fields, but of "
                                + dataStrings.length + " fields. For data: " + ((String) field.get(dataObject)));
                    }
                    logger.trace("Value: " + dataStrings[0]);
                    logger.trace("Unit: " + dataStrings[1]);

                    // BigDecimal value = new BigDecimal(dataStrings[0]).setScale(1, RoundingMode.HALF_UP);
                    BigDecimal value = new BigDecimal(dataStrings[0]);
                    logger.trace("BigDecimal value: " + value);

                    // Now let's find out which unit to use
                    switch (dataStrings[1]) {
                        case "W":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.WATT);
                            break;
                        case "kW":
                            dataValue = new QuantityType<>(value, MetricPrefix.KILO(SmartHomeUnits.WATT));
                            break;
                        case "MW":
                            dataValue = new QuantityType<>(value, MetricPrefix.MEGA(SmartHomeUnits.WATT));
                            break;
                        case "Wh":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.WATT_HOUR);
                            break;
                        case "kWh":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.KILOWATT_HOUR);
                            break;
                        case "MWh":
                            dataValue = new QuantityType<>(value, SmartHomeUnits.MEGAWATT_HOUR);
                            break;
                        case "GWh":
                            dataValue = new QuantityType<>(value, MetricPrefix.GIGA(SmartHomeUnits.WATT_HOUR));
                            break;
                        case "T":
                            dataValue = new QuantityType<>(value, MetricPrefix.MEGA(SIUnits.GRAM));
                            break;
                        case "(€)":
                            break;
                        default:
                            throw new NoSuchFieldException("Unit type of " + dataStrings[1] + " is not a known type.");
                    } // End of unit switch
                    break; // End of string value
                default:
                    throw new NoSuchFieldException("Cannot process fields of type " + fieldType);
            }

            // add the created QuantityType to the list
            result.add(dataValue);

        }

        return result;
    }

    public static Map<DecimalType, StringType> getMonetaireValue(Object dataObject, String objectFieldName)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        HashMap<DecimalType, StringType> result = new HashMap<DecimalType, StringType>();
        Field field = dataObject.getClass().getDeclaredField(objectFieldName);
        Type fieldType = field.getGenericType();
        logger.trace("Field {} is of type {}", field, fieldType);
        switch (fieldType.toString()) {
            case "class java.lang.String":
                String[] dataStrings = ((String) field.get(dataObject)).split("\\s+");
                if (dataStrings.length != 2) {
                    throw new NoSuchFieldException("Response data does not consist of 2 fields, but of "
                            + dataStrings.length + " fields. For data: " + ((String) field.get(dataObject)));
                }
                logger.trace("Value: " + dataStrings[0]);

                logger.trace("Unit: " + dataStrings[1]);
                if (dataStrings[1].startsWith("(") && dataStrings[1].endsWith(")")) {
                    String currencyUnit = dataStrings[1].substring(1, dataStrings[1].length() - 1);
                    logger.trace("Currency unit: " + currencyUnit);
                    result.put(new DecimalType(dataStrings[0]), new StringType(currencyUnit));
                } else {
                    throw new NoSuchFieldException("Cannot process monetairy unit: " + dataStrings[1]);
                }
                break;
            default:
                throw new NoSuchFieldException("Cannot process fields of type " + fieldType);
        }
        return result;
    }

}
