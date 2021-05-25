package org.openlca.app.editors.graphical.themes;

import org.eclipse.swt.graphics.Color;
import org.openlca.app.editors.graphical.model.ExchangeNode;
import org.openlca.app.editors.graphical.model.Link;
import org.openlca.app.editors.graphical.model.ProcessNode;
import org.openlca.app.util.Colors;
import org.openlca.core.model.ModelType;

/**
 * A Monokai color theme, see: https://monokai.pro/
 */
public class DarkTheme implements Theme {

	private static final Color WHITE = Colors.white();
	private static final Color BLACK = Colors.get(44, 41, 45);
	private static final Color PINK = Colors.get(255, 91, 136);
	private static final Color ORANGE = Colors.get(252, 152, 103);
	private static final Color YELLOW = Colors.get(255, 216, 102);
	private static final Color GREEN = Colors.get(169, 220, 118);
	private static final Color BLUE = Colors.get(120, 220, 232);
	private static final Color LILA = Colors.get(171, 157, 242);

	@Override
	public String label() {
		return "Dark";
	}

	@Override
	public String id() {
		return "dark";
	}

	@Override
	public Color defaultFontColor() {
		return WHITE;
	}

	@Override
	public Color defaultBackgroundColor() {
		return BLACK;
	}

	@Override
	public Color defaultBorderColor() {
		return WHITE;
	}

	@Override
	public Color defaultLinkColor() {
		return WHITE;
	}

	@Override
	public Color infoFontColor() {
		return YELLOW;
	}

	@Override
	public Color borderColorOf(ProcessNode node) {
		if (node == null || node.process == null)
			return WHITE;
		var isSystem = node.process.isFromLibrary()
				|| node.process.type == ModelType.PRODUCT_SYSTEM;
		if (isSystem)
			return node.isWasteProcess()
					? PINK
					: LILA;
		return node.isWasteProcess()
				? ORANGE
				: BLUE;
	}

	@Override
	public Color fontColorOf(ExchangeNode node) {
		if (node == null)
			return WHITE;
		var type = node.flowType();
		if (type == null)
			return WHITE;
		return switch (type) {
			case PRODUCT_FLOW -> BLUE;
			case WASTE_FLOW -> ORANGE;
			case ELEMENTARY_FLOW -> GREEN;
			default -> WHITE;
		};
	}
}
