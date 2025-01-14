package org.openlca.app.collaboration.viewers.json.olca;

import java.text.DateFormat;

import org.openlca.app.M;
import org.openlca.app.util.Labels;
import org.openlca.core.model.Exchange;
import org.openlca.core.model.Parameter;
import org.openlca.jsonld.Json;

import com.google.gson.JsonElement;

class ValueLabels {

	private static DateFormat timestampFormatter = DateFormat.getDateTimeInstance();
	private static DateFormat dateFormatter = DateFormat.getDateInstance();

	static String get(String property, JsonElement element, JsonElement parent,
			String value) {
		if (value == null)
			return "null";
		if (EnumFields.isEnum(parent, property)) {
			var enumValue = EnumFields.getEnum(parent, property, value);
			value = Labels.getEnumText(enumValue);
		} else if (DateFields.isDateOrTimestamp(parent, property)) {
			var date = Json.parseDate(value);
			if (date != null) {
				var formatter = DateFields.isDate(parent, property) 
						? dateFormatter 
						: timestampFormatter;
				value = formatter.format(date);
			}
		}
		if (value == null)
			return "null";
		if (isInputParameterField(property, parent))
			return getInputParameterValue(value);
		if (isInputField(property, parent)) {
			return getInputValue(value);
		}
		if (value.equalsIgnoreCase("true"))
			return "Yes";
		if (value.equalsIgnoreCase("false"))
			return "No";
		return value;
	}

	private static boolean isInputParameterField(String property, JsonElement parent) {
		return ModelUtil.isType(parent, Parameter.class) && property.equals("inputParameter");
	}

	private static String getInputParameterValue(String value) {
		if (value.equalsIgnoreCase("true"))
			return M.InputParameter;
		return M.DependenantParameter;
	}

	private static boolean isInputField(String property, JsonElement parent) {
		return ModelUtil.isType(parent, Exchange.class) && property.equals("input");
	}

	private static String getInputValue(String value) {
		if (value.equalsIgnoreCase("true"))
			return M.Input;
		return M.Output;
	}

}
