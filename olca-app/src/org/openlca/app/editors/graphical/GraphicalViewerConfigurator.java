package org.openlca.app.editors.graphical;

import java.util.ArrayList;

import org.eclipse.draw2d.ConnectionLayer;
import org.eclipse.draw2d.Layer;
import org.eclipse.draw2d.LayeredPane;
import org.eclipse.draw2d.StackLayout;
import org.eclipse.draw2d.geometry.Dimension;
import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.KeyHandler;
import org.eclipse.gef.KeyStroke;
import org.eclipse.gef.MouseWheelHandler;
import org.eclipse.gef.MouseWheelZoomHandler;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.editparts.ScalableRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.gef.ui.actions.GEFActionConstants;
import org.eclipse.gef.ui.actions.ZoomInAction;
import org.eclipse.gef.ui.actions.ZoomOutAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.Transfer;
import org.openlca.app.components.ModelTransfer;
import org.openlca.app.editors.graphical.action.ActionFactory;
import org.openlca.app.editors.graphical.model.AppEditPartFactory;
import org.openlca.app.editors.graphical.model.ProductSystemNode;

class GraphicalViewerConfigurator {

	private static final double[] ZOOM_LEVELS = new double[] { 0.005, 0.01,
			0.02, 0.0375, 0.075, 0.125, 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 2.5,
			3.0, 4.0, 5.0, 10.0, 20.0, 40.0, 80.0, 150.0, 300.0, 500.0, 1000.0 };

	private GraphicalViewer viewer;
	private ActionRegistry actionRegistry;
	private CommandStack commandStack;
	private ProductSystemNode model;

	public GraphicalViewerConfigurator(GraphicalViewer viewer) {
		this.viewer = viewer;
	}

	public void setActionRegistry(ActionRegistry actionRegistry) {
		this.actionRegistry = actionRegistry;
	}

	public void setCommandStack(CommandStack commandStack) {
		this.commandStack = commandStack;
	}

	public void setModel(ProductSystemNode model) {
		this.model = model;
	}

	void initializeGraphicalViewer() {
		viewer.setRootEditPart(new ScalableRootEditPart() {

			@Override
			protected LayeredPane createPrintableLayers() {
				final LayeredPane pane = new LayeredPane();

				Layer layer = new ConnectionLayer();
				layer.setPreferredSize(new Dimension(5, 5));
				pane.add(layer, CONNECTION_LAYER);

				layer = new Layer();
				layer.setOpaque(false);
				layer.setLayoutManager(new StackLayout());
				pane.add(layer, PRIMARY_LAYER);

				return pane;
			}

		});

		Transfer transferType = ModelTransfer.getInstance();
		DropTarget dropTarget = new DropTarget(viewer.getControl(),
				DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT);
		dropTarget.setTransfer(new Transfer[] { transferType });
		dropTarget.addDropListener(new GraphDropListener(model, transferType,
				commandStack));
		viewer.setContents(model);
	}

	void configureGraphicalViewer() {
		viewer.setEditPartFactory(new AppEditPartFactory());
		ScalableRootEditPart rootEditPart = new ScalableRootEditPart();
		viewer.setRootEditPart(rootEditPart);
	}

	void configureActions() {
		actionRegistry.registerAction(ActionFactory
				.createBuildSupplyChainMenuAction(model));
		actionRegistry.registerAction(ActionFactory
				.createRemoveSupplyChainAction(null));
		actionRegistry.registerAction(ActionFactory
				.createRemoveAllConnectionsAction());
		actionRegistry.registerAction(new ZoomInAction(getZoomManager()));
		actionRegistry.registerAction(new ZoomOutAction(getZoomManager()));

		// DeleteAction delAction = new DeleteAction((IWorkbenchPart) this) {
		//
		// @Override
		// protected ISelection getSelection() {
		// return selectionService.getSelection();
		// }
		//
		// };
		// actionRegistry.registerAction(delAction);
	}

	void configureZoomManager() {
		getZoomManager().setZoomLevels(ZOOM_LEVELS);
		ArrayList<String> zoomContributions = new ArrayList<>();
		zoomContributions.add(ZoomManager.FIT_ALL);
		zoomContributions.add(ZoomManager.FIT_HEIGHT);
		zoomContributions.add(ZoomManager.FIT_WIDTH);
		getZoomManager().setZoomLevelContributions(zoomContributions);
		viewer.setProperty(MouseWheelHandler.KeyGenerator.getKey(SWT.NONE),
				MouseWheelZoomHandler.SINGLETON);
	}

	void configureKeyHandler() {
		KeyHandler keyHandler = new KeyHandler();
		keyHandler
				.put(KeyStroke.getPressed(SWT.DEL, 127, 0), actionRegistry
						.getAction(org.eclipse.ui.actions.ActionFactory.DELETE
								.getId()));
		keyHandler.put(KeyStroke.getPressed('+', SWT.KEYPAD_ADD, 0),
				actionRegistry.getAction(GEFActionConstants.ZOOM_IN));
		keyHandler.put(KeyStroke.getPressed('-', SWT.KEYPAD_SUBTRACT, 0),
				actionRegistry.getAction(GEFActionConstants.ZOOM_OUT));
		viewer.setKeyHandler(keyHandler);
	}

	void configureContextMenu() {
		ContextMenuProvider provider = new AppContextMenuProvider(viewer,
				actionRegistry);
		viewer.setContextMenu(provider);
	}

	private ZoomManager getZoomManager() {
		return getRootEditPart().getZoomManager();
	}

	private ScalableRootEditPart getRootEditPart() {
		return (ScalableRootEditPart) viewer.getRootEditPart();
	}
}
