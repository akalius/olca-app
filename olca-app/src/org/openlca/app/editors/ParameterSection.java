package org.openlca.app.editors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.Event;
import org.openlca.app.Messages;
import org.openlca.app.components.UncertaintyCellEditor;
import org.openlca.app.util.Actions;
import org.openlca.app.util.Dialog;
import org.openlca.app.util.Error;
import org.openlca.app.util.Tables;
import org.openlca.app.util.UI;
import org.openlca.app.util.UncertaintyLabel;
import org.openlca.app.util.Viewers;
import org.openlca.app.viewers.table.modify.ModifySupport;
import org.openlca.app.viewers.table.modify.TextCellModifier;
import org.openlca.core.model.Parameter;
import org.openlca.expressions.FormulaInterpreter;
import org.openlca.util.Strings;

import com.google.common.eventbus.Subscribe;

/**
 * A section with a table for parameters in processes and LCIA methods. It is
 * possible to create two kinds of tables with this class: for input parameters
 * with the columns name, value, and description, or for dependent parameters
 * with the columns name, formula, value, and description.
 */
public class ParameterSection {

	private TableViewer viewer;

	private final String NAME = Messages.Name;
	private final String VALUE = Messages.Value;
	private final String FORMULA = Messages.Formula;
	private final String UNCERTAINTY = Messages.Uncertainty;
	private final String DESCRIPTION = Messages.Description;

	private boolean forInputParameters = true;
	private ParameterPageInput input;
	private ModelEditor<?> editor;
	private List<Parameter> parameters;

	public static ParameterSection forInputParameters(ParameterPageInput input,
			Composite body) {
		ParameterSection table = new ParameterSection(input, body, true);
		return table;
	}

	public static ParameterSection forDependentParameters(
			ParameterPageInput input, Composite body) {
		ParameterSection table = new ParameterSection(input, body, false);
		return table;
	}

	private ParameterSection(ParameterPageInput input, Composite body,
			boolean forInputParams) {
		this.forInputParameters = forInputParams;
		this.editor = input.getEditor();
		this.input = input;
		this.parameters = input.getParameters();
		String[] props = {};
		if (forInputParams)
			props = new String[] { NAME, VALUE, UNCERTAINTY, DESCRIPTION };
		else
			props = new String[] { NAME, FORMULA, VALUE, DESCRIPTION };
		createComponents(body, props);
		createCellModifiers();
		fillInitialInput();
		input.getEditor().getEventBus().register(this);
	}

	@Subscribe
	public void handleEvaluation(Event event) {
		if (event.match(input.getEditor().FORMULAS_EVALUATED))
			viewer.refresh();
	}

	private void createComponents(Composite body, String[] properties) {
		String label = forInputParameters ? "Input parameters"
				: "Dependent parameters";
		Section section = UI.section(body, editor.getToolkit(), label);
		UI.gridData(section, true, true);
		Composite parent = UI.sectionClient(section, editor.getToolkit());
		viewer = Tables.createViewer(parent, properties);
		viewer.setLabelProvider(new ParameterLabelProvider());
		Table table = viewer.getTable();
		if (forInputParameters)
			Tables.bindColumnWidths(table, 0.3, 0.3, 0.2, 0.2);
		else
			Tables.bindColumnWidths(table, 0.3, 0.3, 0.2, 0.2);
		bindActions(section);
	}

	private void bindActions(Section section) {
		Action addAction = Actions.onAdd(new Runnable() {
			public void run() {
				addParameter();
			}
		});
		Action removeAction = Actions.onRemove(new Runnable() {
			public void run() {
				removeParameter();
			}
		});
		Actions.bind(section, addAction, removeAction);
		Actions.bind(viewer, addAction, removeAction);
	}

	private void createCellModifiers() {
		ModifySupport<Parameter> modifySupport = new ModifySupport<>(viewer);
		modifySupport.bind(NAME, new NameModifier());
		modifySupport.bind(DESCRIPTION, new DescriptionModifier());
		if (forInputParameters) {
			modifySupport.bind(VALUE, new ValueModifier());
			modifySupport.bind(UNCERTAINTY,
					new UncertaintyCellEditor(viewer.getTable(), editor));
		} else
			modifySupport.bind(FORMULA, new FormulaModifier());
	}

	private void fillInitialInput() {
		// when the viewer is created, we first sort the parameters by name
		Collections.sort(parameters, new Comparator<Parameter>() {
			@Override
			public int compare(Parameter o1, Parameter o2) {
				return Strings.compare(o1.getName(), o2.getName());
			}
		});
		setInput();
	}

	private void setInput() {
		List<Parameter> input = new ArrayList<>();
		for (Parameter param : parameters) {
			if (param.isInputParameter() == forInputParameters)
				input.add(param);
		}
		viewer.setInput(input);
	}

	private void addParameter() {
		Parameter parameter = new Parameter();
		parameter.setName("p_" + parameters.size());
		parameter.setScope(input.getScope());
		parameter.setInputParameter(forInputParameters);
		parameter.setValue(1.0);
		if (!forInputParameters) {
			parameter.setFormula("1.0");
		}
		parameters.add(parameter);
		setInput();
		fireChange();
	}

	private void removeParameter() {
		List<Parameter> selection = Viewers.getAllSelected(viewer);
		for (Parameter parameter : selection) {
			parameters.remove(parameter);
		}
		setInput();
		fireChange();
	}

	private void fireChange() {
		editor.setDirty(true);
		editor.postEvent(editor.PARAMETER_CHANGE, this);
	}

	private class ParameterLabelProvider extends LabelProvider implements
			ITableLabelProvider {
		@Override
		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}

		@Override
		public String getColumnText(Object element, int columnIndex) {
			if (!(element instanceof Parameter))
				return null;
			Parameter parameter = (Parameter) element;
			switch (columnIndex) {
			case 0:
				return parameter.getName();
			case 1:
				if (forInputParameters)
					return Double.toString(parameter.getValue());
				else
					return parameter.getFormula();
			case 2:
				if (forInputParameters)
					return UncertaintyLabel.get(parameter.getUncertainty());
				else
					return Double.toString(parameter.getValue());
			case 3:
				return parameter.getDescription();
			default:
				return null;
			}
		}
	}

	private class NameModifier extends TextCellModifier<Parameter> {
		@Override
		protected String getText(Parameter param) {
			return param.getName();
		}

		@Override
		protected void setText(Parameter param, String text) {
			if (text == null)
				return;
			if (Objects.equals(text, param.getName()))
				return;
			String name = text.trim();
			if (!Parameter.isValidName(name)) {
				Error.showBox("Invalid parameter name", name
						+ " is not a valid parameter name");
				return;
			}
			param.setName(name);
			fireChange();
		}
	}

	private class ValueModifier extends TextCellModifier<Parameter> {
		@Override
		protected String getText(Parameter param) {
			return Double.toString(param.getValue());
		}

		@Override
		protected void setText(Parameter param, String text) {
			try {
				double d = Double.parseDouble(text);
				param.setValue(d);
				fireChange();
			} catch (Exception e) {
				Dialog.showError(viewer.getTable().getShell(), text
						+ " is not a valid number. ");
			}
		}
	}

	private class FormulaModifier extends TextCellModifier<Parameter> {
		@Override
		protected String getText(Parameter param) {
			return param.getFormula();
		}

		@Override
		protected void setText(Parameter param, String formula) {
			try {
				FormulaInterpreter interpreter = input.getInterpreter();
				long scope = editor.getModel().getId();
				double val = interpreter.getScope(scope).eval(formula);
				param.setFormula(formula);
				param.setValue(val);
				fireChange();
			} catch (Exception e) {
				Error.showBox("Invalid formula",
						Strings.cut(e.getMessage(), 75));
			}
		}
	}

	private class DescriptionModifier extends TextCellModifier<Parameter> {
		@Override
		protected String getText(Parameter param) {
			return param.getDescription();
		}

		@Override
		protected void setText(Parameter param, String text) {
			if (!Objects.equals(text, param.getDescription())) {
				param.setDescription(text);
				fireChange();
			}
		}
	}

}