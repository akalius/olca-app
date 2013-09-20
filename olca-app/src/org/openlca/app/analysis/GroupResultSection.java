package org.openlca.app.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.openlca.app.Messages;
import org.openlca.app.components.ContributionItem;
import org.openlca.app.components.charts.ContributionChart;
import org.openlca.app.db.Database;
import org.openlca.app.util.UI;
import org.openlca.app.viewers.ISelectionChangedListener;
import org.openlca.app.viewers.combo.AbstractComboViewer;
import org.openlca.app.viewers.combo.FlowViewer;
import org.openlca.app.viewers.combo.ImpactCategoryViewer;
import org.openlca.core.database.EntityCache;
import org.openlca.core.model.descriptors.FlowDescriptor;
import org.openlca.core.model.descriptors.ImpactCategoryDescriptor;
import org.openlca.core.results.AnalysisResult;
import org.openlca.core.results.Contribution;
import org.openlca.core.results.GroupingContribution;
import org.openlca.core.results.ProcessGrouping;

class GroupResultSection {

	private int FLOW = 0;
	private int IMPACT = 1;
	private int resultType = 0;

	private EntityCache cache = Database.getEntityCache();
	private List<ProcessGrouping> groups;
	private AnalysisResult result;
	private TableViewer tableViewer;
	private FlowViewer flowViewer;
	private ImpactCategoryViewer impactViewer;
	private ContributionChart chart;

	public GroupResultSection(List<ProcessGrouping> groups,
			AnalysisResult result) {
		this.groups = groups;
		this.result = result;
	}

	public void update() {
		Object selection = null;
		if (resultType == FLOW)
			selection = flowViewer.getSelected();
		else
			selection = impactViewer.getSelected();
		if (selection != null && tableViewer != null) {
			List<Contribution<ProcessGrouping>> items = calculate(selection);
			tableViewer.setInput(items);
			List<ContributionItem> chartData = createChartData(items);
			chart.setData(chartData);
		}
	}

	private List<Contribution<ProcessGrouping>> calculate(Object selection) {
		GroupingContribution calculator = new GroupingContribution(result,
				groups);
		if (selection instanceof FlowDescriptor)
			return calculator.calculate((FlowDescriptor) selection)
					.getContributions();
		if (selection instanceof ImpactCategoryDescriptor)
			return calculator.calculate((ImpactCategoryDescriptor) selection)
					.getContributions();
		return Collections.emptyList();
	}

	private List<ContributionItem> createChartData(
			List<Contribution<ProcessGrouping>> items) {
		List<ContributionItem> data = new ArrayList<>();
		for (Contribution<ProcessGrouping> item : items) {
			ContributionItem dataItem = new ContributionItem();
			dataItem.setAmount(item.getAmount());
			dataItem.setContribution(item.getShare());
			dataItem.setLabel(item.getItem().getName());
			dataItem.setRest(item.getItem().isRest());
			data.add(dataItem);
			// dataItem.setUnit(); TODO: units in chart
		}
		return data;
	}

	public void render(Composite body, FormToolkit toolkit) {
		Section section = UI.section(body, toolkit, Messages.Results);
		UI.gridData(section, true, true);
		Composite client = UI.sectionClient(section, toolkit);
		UI.gridLayout(client, 1);
		createCombos(toolkit, client);
		GroupResultTable table = new GroupResultTable(client);
		tableViewer = table.getViewer();
		UI.gridData(tableViewer.getControl(), true, false).heightHint = 200;
		createChartSection(client, toolkit);
		update();
	}

	private void createChartSection(Composite parent, FormToolkit toolkit) {
		Composite composite = toolkit.createComposite(parent);
		UI.gridData(composite, true, true);
		chart = new ContributionChart(composite, toolkit);
	}

	private void createCombos(FormToolkit toolkit, Composite client) {
		Composite composite = toolkit.createComposite(client);
		UI.gridData(composite, true, false);
		UI.gridLayout(composite, 2);
		createFlowViewer(toolkit, composite);
		if (result.hasImpactResults())
			createImpact(toolkit, composite);
	}

	private void createFlowViewer(FormToolkit toolkit, Composite parent) {
		Button flowsCheck = toolkit.createButton(parent, Messages.Flows,
				SWT.RADIO);
		flowsCheck.setSelection(true);
		flowViewer = new FlowViewer(parent, cache);
		Set<FlowDescriptor> flows = result.getFlowResults().getFlows(cache);
		flowViewer.setInput(flows.toArray(new FlowDescriptor[flows.size()]));
		flowViewer
				.addSelectionChangedListener(new SelectionChange<FlowDescriptor>());
		if (flows.size() > 0)
			flowViewer.select(flows.iterator().next());
		new ResultTypeCheck(flowViewer, flowsCheck, FLOW);
	}

	private void createImpact(FormToolkit toolkit, Composite parent) {
		Button impactCheck = toolkit.createButton(parent,
				Messages.ImpactCategories, SWT.RADIO);
		impactViewer = new ImpactCategoryViewer(parent);
		impactViewer.setEnabled(false);
		Set<ImpactCategoryDescriptor> impacts = result.getImpactResults()
				.getImpacts(cache);
		impactViewer.setInput(impacts);
		impactViewer
				.addSelectionChangedListener(new SelectionChange<ImpactCategoryDescriptor>());
		if (impacts.size() > 0)
			impactViewer.select(impacts.iterator().next());
		new ResultTypeCheck(impactViewer, impactCheck, IMPACT);
	}

	private class SelectionChange<T> implements ISelectionChangedListener<T> {

		@Override
		public void selectionChanged(T value) {
			update();
		}
	}

	private class ResultTypeCheck implements SelectionListener {

		private AbstractComboViewer<?> viewer;
		private Button check;
		private int type;

		public ResultTypeCheck(AbstractComboViewer<?> viewer, Button check,
				int type) {
			this.viewer = viewer;
			this.check = check;
			this.type = type;
			check.addSelectionListener(this);
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
			widgetSelected(e);
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			if (check.getSelection()) {
				viewer.setEnabled(true);
				resultType = this.type;
				update();
			} else
				viewer.setEnabled(false);
		}
	}
}
