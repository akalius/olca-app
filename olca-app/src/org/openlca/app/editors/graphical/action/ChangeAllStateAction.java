package org.openlca.app.editors.graphical.action;

import org.eclipse.gef.commands.Command;
import org.eclipse.jface.viewers.ISelection;
import org.openlca.app.M;
import org.openlca.app.editors.graphical.command.ChangeStateCommand;
import org.openlca.app.editors.graphical.command.Commands;
import org.openlca.app.editors.graphical.model.ProcessNode;
import org.openlca.app.rcp.images.Icon;

class ChangeAllStateAction extends EditorAction {

	static final int MINIMIZE = 1;
	static final int MAXIMIZE = 2;
	private final int type;

	ChangeAllStateAction(int type) {
		if (type == MINIMIZE) {
			setId(ActionIds.MINIMIZE_ALL);
			setText(M.MinimizeAll);
			setImageDescriptor(Icon.MINIMIZE.descriptor());
		} else if (type == MAXIMIZE) {
			setId(ActionIds.MAXIMIZE_ALL);
			setText(M.MaximizeAll);
			setImageDescriptor(Icon.MAXIMIZE.descriptor());
		}
		this.type = type;
	}

	@Override
	public void run() {
		Command actualCommand = null;
		for (ProcessNode node : editor.getModel().getChildren()) {
			boolean minimize = type == MINIMIZE;
			if (node.isMinimized() == minimize)
				continue;
			ChangeStateCommand newCommand = new ChangeStateCommand(node);
			actualCommand = Commands.chain(newCommand, actualCommand);
		}
		if (actualCommand == null)
			return;
		editor.getCommandStack().execute(actualCommand);
	}

	@Override
	protected boolean accept(ISelection selection) {
		return true;
	}

}
