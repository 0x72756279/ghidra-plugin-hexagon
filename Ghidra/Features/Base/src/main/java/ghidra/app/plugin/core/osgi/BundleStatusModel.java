/* ###
 * IP: GHIDRA
 * REVIEWED: YES
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.osgi;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;

import docking.widgets.table.AbstractSortedTableModel;
import generic.jar.ResourceFile;
import ghidra.app.script.GhidraScriptUtil;
import ghidra.framework.options.SaveState;
import ghidra.framework.preferences.Preferences;
import ghidra.util.Msg;

public class BundleStatusModel extends AbstractSortedTableModel<BundleStatus> {
	List<Column> columns = new ArrayList<>();

	class Column {
		final Class<?> clazz;
		final int index;
		final String name;

		Column(String name, Class<?> clazz) {
			this.name = name;
			this.index = columns.size();
			columns.add(this);
			this.clazz = clazz;
		}

		boolean editable(BundleStatus status) {
			return false;
		}

		Object getValue(BundleStatus status) {
			return null;
		}

		void setValue(BundleStatus status, Object aValue) {
			throw new RuntimeException(name + " is not editable!");
		}

	}

	Column enabledColumn = new Column("Enabled", Boolean.class) {
		@Override
		boolean editable(BundleStatus status) {
			return status.pathExists();
		}

		@Override
		Object getValue(BundleStatus status) {
			return status.isEnabled();
		}

		@Override
		void setValue(BundleStatus status, Object newValue) {
			status.setEnabled((Boolean) newValue);
			fireBundleEnablementChanged(status, (Boolean) newValue);
		}
	};
	Column activeColumn = new Column("Active", Boolean.class) {
		@Override
		boolean editable(BundleStatus status) {
			return status.pathExists(); // XXX maybe only if it's already enabled
		}

		@Override
		Object getValue(BundleStatus status) {
			return status.isActive();
		}

		@Override
		void setValue(BundleStatus status, Object newValue) {
			status.setActive((Boolean) newValue);
			fireBundleActivationChanged(status, (Boolean) newValue);
		}
	};
	Column typeColumn = new Column("Type", String.class) {
		@Override
		Object getValue(BundleStatus status) {
			return status.getType().toString();
		}
	};

	Column pathColumn = new Column("Path", ResourceFile.class) {
		@Override
		Object getValue(BundleStatus status) {
			return status.getPath();
		}
	};
	Column summaryColumn = new Column("Summary", String.class) {
		@Override
		Object getValue(BundleStatus status) {
			return status.getSummary();
		}
	};

	Column badColumn = new Column("INVALID", Object.class);
	{
		columns.remove(columns.size() - 1); // pop badColumn

	}

	Column getColumn(int i) {
		if (i >= 0 && i < columns.size()) {
			return columns.get(i);
		}
		return badColumn;
	}

	private BundleStatusProvider provider;
	private List<BundleStatus> statuses;
	private BundleHost bundleHost;
	OSGiListener bundleListener;

	private Map<String, BundleStatus> loc2status = new HashMap<>();

	BundleStatus getStatus(String bundleLocation) {
		return loc2status.get(bundleLocation);
	}

	public String getBundleLoc(BundleStatus status) {
		return bundleHost.getGhidraBundle(status.getPath()).getBundleLoc();
	}

	/** 
	 * (re)compute cached mapping from bundleloc to bundlepath
	 */
	private void computeCache() {
		loc2status.clear();
		for (BundleStatus status : statuses) {
			String loc = getBundleLoc(status);
			if (loc != null) {
				loc2status.put(loc, status);
			}
		}
	}

	BundleStatusModel(BundleStatusProvider provider, BundleHost bundleHost) {
		super();
		this.provider = provider;
		this.bundleHost = bundleHost;

		// add unmodifiable paths
		this.statuses = GhidraScriptUtil.getSystemScriptPaths().stream().distinct().map(
			f -> new BundleStatus(f, true, true)).collect(Collectors.toList());
		// add user path
		this.statuses.add(0,
			new BundleStatus(GhidraScriptUtil.getUserScriptDirectory(), true, false));

		computeCache();

		bundleHost.addListener(bundleListener = new OSGiListener() {
			@Override
			public void sourceBundleCompiled(GhidraSourceBundle sb) {
				BundleStatus bp = getStatus(sb.getBundleLoc());
				if (bp != null) {
					bp.setSummary(sb.getSummary());
					int row = getRowIndex(bp);
					fireTableRowsUpdated(row, row);
				}
			}

			@Override
			public void bundleActivationChange(Bundle b, boolean newActivation) {
				BundleStatus bp = getStatus(b.getLocation());
				if (newActivation) {
					if (bp != null) {
						bp.setActive(true);
						int row = getRowIndex(bp);
						fireTableRowsUpdated(row, row);
					}
				}
				else {
					if (bp != null) {
						bp.setActive(false);
						int row = getRowIndex(bp);
						fireTableRowsUpdated(row, row);
					}
				}

			}
		});

		fireTableDataChanged();
	}

	@Override
	public void dispose() {
		super.dispose();
		bundleHost.removeListener(bundleListener);
	}

	public List<ResourceFile> getEnabledPaths() {
		List<ResourceFile> list = new ArrayList<>();
		for (BundleStatus status : statuses) {
			if (status.isEnabled()) {
				list.add(status.getPath());
			}
		}
		return list;
	}

	private void addStatus(BundleStatus path) {
		if (statuses.contains(path)) {
			return;
		}
		String loc = getBundleLoc(path);
		if (loc != null) {
			loc2status.put(loc, path);
		}

		int index = statuses.size();
		statuses.add(path);
		fireTableRowsInserted(index, index);
	}

	private BundleStatus addNewStatus(ResourceFile path, boolean enabled, boolean readonly) {
		BundleStatus p = new BundleStatus(path, enabled, readonly);
		addStatus(p);
		return p;
	}

	private BundleStatus addNewStatus(String path, boolean enabled, boolean readonly) {
		BundleStatus p = new BundleStatus(path, enabled, readonly);
		addStatus(p);
		return p;
	}

	/**
	 * create new BundleStatus objects for each of the given files
	 * 
	 * @param files the files.. given...
	 * @param enabled mark them all as enabled
	 * @param readonly mark them all as readonly
	 */
	void addNewPaths(List<File> files, boolean enabled, boolean readonly) {
		for (File f : files) {
			BundleStatus status = new BundleStatus(new ResourceFile(f), enabled, readonly);
			addStatus(status);
		}
		fireBundlesChanged();
	}

	void remove(int[] selectedRows) {
		List<BundleStatus> toRemove = new ArrayList<>();
		for (int selectedRow : selectedRows) {
			toRemove.add(statuses.get(selectedRow));
		}
		for (BundleStatus status : toRemove) {
			if (!status.isReadOnly()) {
				statuses.remove(status);
				loc2status.remove(getBundleLoc(status));
			}
			else {
				Msg.showInfo(this, this.provider.getComponent(), "Unabled to remove path",
					"System path cannot be removed: " + status.toString());
			}
		}
		fireTableDataChanged();
		fireBundlesChanged();
	}

	/***************************************************/

	@Override
	public int getColumnCount() {
		return columns.size();
	}

	@Override
	public int getRowCount() {
		return statuses.size();
	}

	@Override
	public java.lang.Class<?> getColumnClass(int columnIndex) {
		return getColumn(columnIndex).clazz;
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		BundleStatus status = statuses.get(rowIndex);
		return getColumn(columnIndex).editable(status);
	}

	@Override
	public String getColumnName(int columnIndex) {
		return getColumn(columnIndex).name;
	}

	@Override
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		BundleStatus status = statuses.get(rowIndex);
		getColumn(columnIndex).setValue(status, aValue);
		// XXX: avoid RowObjectSelectionManager.repairSelection by selecting the row we're editing
		provider.selectRow(rowIndex);
	}

	@Override
	public Object getColumnValueForRow(BundleStatus status, int columnIndex) {
		return getColumn(columnIndex).getValue(status);
	}

	@Override
	public boolean isSortable(int columnIndex) {
		return true;
	}

	@Override
	public String getName() {
		return BundleStatusModel.class.getSimpleName();
	}

	@Override
	public List<BundleStatus> getModelData() {
		return statuses;
	}

	/**
	 * (add and) enable a path
	 * @param file path to enable 
	 * @return true if the path is new
	 */
	public boolean enablePath(ResourceFile file) {
		ResourceFile dir = file.isDirectory() ? file : file.getParentFile();
		for (BundleStatus path : statuses) {
			if (path.getPath().equals(dir)) {
				if (!path.isEnabled()) {
					path.setEnabled(true);
					fireTableDataChanged();
					fireBundlesChanged();
					return true;
				}
				return false;
			}
		}
		addNewStatus(dir, true, false);
		Preferences.setProperty(BundleStatusProvider.preferenceForLastSelectedBundle,
			dir.getAbsolutePath());
		fireBundlesChanged();
		return true;
	}

	/**
	 * Test whether the given <code>bundle</code> is managed and not marked readonly
	 * @param bundle the path to test 
	 * @return true if the bundle is managed and not marked readonly
	 */
	public boolean isWriteable(ResourceFile bundle) {
		Optional<BundleStatus> o = statuses.stream().filter(
			status -> status.isDirectory() && status.getPath().equals(bundle)).findFirst();
		return o.isPresent() && !o.get().isReadOnly();
	}

	/**
	 * This is for testing only!
	 * 
	 * each path is marked editable and non-readonly
	 * 
	 * @param testingPaths the statuses to use
	 */
	public void setPathsForTesting(List<String> testingPaths) {
		this.statuses = testingPaths.stream().map(f -> new BundleStatus(f, true, false)).collect(
			Collectors.toList());
		computeCache();
		fireTableDataChanged();
	}

	/**
	 * This is for testing only!
	 * 
	 * insert path, marked editable and non-readonly
	 * @param path the path to insert
	 */
	public void insertPathForTesting(String path) {
		addNewStatus(path, true, false);
	}

	private ArrayList<BundleStatusListener> listeners = new ArrayList<>();

	public void addListener(BundleStatusListener listener) {
		synchronized (listeners) {
			if (!listeners.contains(listener)) {
				listeners.add(listener);
			}
		}
	}

	public void removeListener(BundleStatusListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	private void fireBundlesChanged() {
		synchronized (listeners) {
			for (BundleStatusListener listener : listeners) {
				listener.bundlesChanged();
			}
		}
	}

	void fireBundleEnablementChanged(BundleStatus path, boolean newValue) {
		synchronized (listeners) {
			for (BundleStatusListener listener : listeners) {
				listener.bundleEnablementChanged(path, newValue);
			}
		}
	}

	void fireBundleActivationChanged(BundleStatus path, boolean newValue) {
		synchronized (listeners) {
			for (BundleStatusListener listener : listeners) {
				listener.bundleActivationChanged(path, newValue);
			}
		}
	}

	/**
	 * Restores the statuses from the specified SaveState object.
	 * @param ss the SaveState object
	 */
	public void restoreState(SaveState ss) {
		String[] pathArr = ss.getStrings("BundleStatus_PATH", new String[0]);

		if (pathArr.length == 0) {
			return;
		}

		boolean[] enableArr = ss.getBooleans("BundleStatus_ENABLE", new boolean[pathArr.length]);
		boolean[] readonlyArr = ss.getBooleans("BundleStatus_READ", new boolean[pathArr.length]);

		List<BundleStatus> currentPaths = new ArrayList<>(statuses);
		statuses.clear();

		for (int i = 0; i < pathArr.length; i++) {
			BundleStatus currentStatus = getStatus(pathArr[i], currentPaths);
			if (currentStatus != null) {
				currentPaths.remove(currentStatus);
				addNewStatus(pathArr[i], enableArr[i], readonlyArr[i]);
			}
			else if (!readonlyArr[i]) {
				// skip read-only statuses which are not present in the current config
				// This is needed to thin-out old default entries
				addNewStatus(pathArr[i], enableArr[i], readonlyArr[i]);
			}
		}
		fireBundlesChanged();
	}

	private static BundleStatus getStatus(String filepath, List<BundleStatus> statuses) {
		for (BundleStatus status : statuses) {
			if (filepath.equals(status.getPathAsString())) {
				return status;
			}
		}
		return null;
	}

	/**
	 * Saves the statuses to the specified SaveState object.
	 * @param ss the SaveState object
	 */
	public void saveState(SaveState ss) {
		String[] pathArr = new String[statuses.size()];
		boolean[] enableArr = new boolean[statuses.size()];
		boolean[] readonlyArr = new boolean[statuses.size()];

		int index = 0;
		for (BundleStatus status : statuses) {
			pathArr[index] = status.getPathAsString();
			enableArr[index] = status.isEnabled();
			readonlyArr[index] = status.isReadOnly();
			++index;
		}

		ss.putStrings("BundleStatus_PATH", pathArr);
		ss.putBooleans("BundleStatus_ENABLE", enableArr);
		ss.putBooleans("BundleStatus_READ", readonlyArr);
	}
}