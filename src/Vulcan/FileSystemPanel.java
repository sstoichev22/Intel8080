package Vulcan;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class FileSystemPanel extends JPanel {
    private final SettingsManager settings;
    private final JTree tree;
    private final DefaultTreeModel model;
    private final DefaultMutableTreeNode rootNode;
    private final Consumer<Path> onOpen;
    private final LocalFileRenderer renderer;
    private final JLabel pathLabel;
    private final JLabel title;
    private final String fontKey;
    private final JButton backButton;
    private final Clipboard clipboardOverride;
    private final ProjectFileOperations.History fileHistory = new ProjectFileOperations.History();
    private java.util.function.BiConsumer<Path, Path> moveListener = (from, to) -> {};
    private static final DataFlavor FILE_OPERATION = new DataFlavor(
            DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Object", "Vulcan file operation");
    private Consumer<Path> directoryListener = path -> {};
    private Consumer<Path> deletionListener = path -> {};
    private Path projectDirectory;

    /** Creates a project browser that opens selected files through the supplied callback. */
    public FileSystemPanel(SettingsManager settings, Consumer<Path> onOpen) {
        this(settings, onOpen, "editorFilePanel");
    }

    /** Creates a project browser with an independent persisted font-size group. */
    public FileSystemPanel(SettingsManager settings, Consumer<Path> onOpen, String fontKey) {
        this(settings, onOpen, fontKey, null);
    }

    /** Creates a browser with an optional isolated clipboard for filesystem checks. */
    FileSystemPanel(SettingsManager settings, Consumer<Path> onOpen, String fontKey, Clipboard clipboard) {
        this.clipboardOverride = clipboard;
        this.settings = settings;
        this.onOpen = onOpen == null ? path -> {} : onOpen;
        this.fontKey = fontKey == null ? "editorFilePanel" : fontKey;
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(0, 0));
        setBorder(null);
        setBackground(settings.getColor("panelBackground"));

        title = new JLabel("Files");
        title.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize(this.fontKey + "Title")));
        title.setForeground(settings.getColor("text"));
        pathLabel = new JLabel("");
        pathLabel.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize(this.fontKey + "Path")));
        pathLabel.setForeground(settings.getColor("tabTextMuted"));
        pathLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));

        JPanel labels = new JPanel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        labels.setOpaque(false);
        labels.add(title);
        labels.add(pathLabel);

        backButton = new JButton(Icons.vector("back", 16, settings.getColor("text")));
        backButton.setToolTipText("Back");
        backButton.setPreferredSize(new Dimension(30, 25));
        backButton.setFocusPainted(false);
        backButton.addActionListener(event -> navigateBack());
        JButton browse = new JButton("...");
        browse.setToolTipText("Choose project folder in File Explorer");
        browse.setPreferredSize(new Dimension(30, 25));
        browse.setFocusPainted(false);
        JButton explorer = new JButton("↗");
        explorer.setToolTipText("Open folder in File Explorer");
        explorer.setPreferredSize(new Dimension(30, 25));
        explorer.setFocusPainted(false);
        JButton copy = new JButton(Icons.fit("ctc-icon.png", 16, 16));
        copy.setToolTipText("Copy project path");
        copy.setPreferredSize(new Dimension(30, 25));
        copy.setFocusPainted(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        buttons.setOpaque(false);
        buttons.add(backButton);
        buttons.add(copy);
        buttons.add(browse);
        buttons.add(explorer);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 6));
        top.setBackground(settings.getColor("panelBackground"));
        top.add(labels, BorderLayout.CENTER);
        top.add(buttons, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        rootNode = new DefaultMutableTreeNode();
        model = new DefaultTreeModel(rootNode);
        tree = new JTree(model);
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(Math.max(18, settings.getFontSize(this.fontKey) + 9));
        tree.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize(this.fontKey)));
        tree.setBorder(null);
        tree.setOpaque(true);
        renderer = new LocalFileRenderer(settings, this.fontKey);
        tree.setCellRenderer(renderer);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        installFileActions();
        tree.setTransferHandler(new FileDropHandler());
        tree.setDropMode(DropMode.ON_OR_INSERT);

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(settings.getColor("inputBackground"));
        Theme.styleScrollBar(scroll.getVerticalScrollBar(), settings);
        Theme.styleScrollBar(scroll.getHorizontalScrollBar(), settings);
        add(scroll, BorderLayout.CENTER);

        projectDirectory = settings.getCurrentDirectory();
        if (!Files.isDirectory(projectDirectory)) projectDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        setProjectDirectory(projectDirectory, false);

        browse.addActionListener(e -> chooseProjectFolder());
        explorer.addActionListener(e -> openInExplorer());
        copy.addActionListener(e -> copyPath());
        tree.addMouseWheelListener(this::handleZoom);
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override public void treeWillExpand(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                loadChildren(node);
            }
            @Override public void treeWillCollapse(TreeExpansionEvent event) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) event.getPath().getLastPathComponent();
                if (node.getChildCount() == 0 && node.getUserObject() instanceof File f && isTreeDirectory(f)) node.add(new DefaultMutableTreeNode());
            }
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == 4) return;
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                DefaultMutableTreeNode node = nodeAt(e);
                if (node == null || !(node.getUserObject() instanceof File file)) return;
                TreePath path = new TreePath(node.getPath());
                if (isTreeDirectory(file)) {
                    if (e.getClickCount() >= 2) {
                        setProjectDirectory(file.toPath(), true);
                    } else if (tree.isExpanded(path)) {
                        tree.collapsePath(path);
                    } else {
                        tree.expandPath(path);
                    }
                } else if (e.getClickCount() >= 2) onOpen.accept(file.toPath());
            }
            @Override public void mousePressed(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { if (e.isPopupTrigger()) showPopup(e); }
        });
        installMouseBack(this);
    }

    /** Navigates up exactly one folder only in response to an explicit Back action. */
    private void navigateBack() {
        Path parent = projectDirectory == null ? null : projectDirectory.getParent();
        if (parent != null && Files.isDirectory(parent)) setProjectDirectory(parent, true);
    }

    /** Handles the mouse Back button on its first press anywhere inside this file panel. */
    private void installMouseBack(Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (event.getButton() == 4 && !event.isConsumed()) { event.consume(); navigateBack(); }
            }
        });
        if (component instanceof Container container)
            for (Component child : container.getComponents()) installMouseBack(child);
    }

    /** Binds standard file operations to the tree selection and keyboard shortcuts. */
    private void installFileActions() {
        bindTreeAction("control C", "copy-files", () -> copySelectedFiles(false));
        bindTreeAction("control X", "cut-files", () -> copySelectedFiles(true));
        bindTreeAction("control V", "paste-files", () -> pasteClipboard(selectedDirectory()));
        bindTreeAction("control Z", "undo-files", this::undoFileOperation);
        bindTreeAction("control D", "duplicate-files", this::duplicateSelected);
        bindTreeAction("DELETE", "delete-files", this::deleteSelected);
    }

    /** Registers a keyboard shortcut for one file-tree operation. */
    private void bindTreeAction(String key, String name, Runnable action) {
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        Action binding = new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { action.run(); }
        };
        tree.getActionMap().put(name, binding);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), name);
        getActionMap().put(name, binding);
    }

    private Clipboard clipboard() { return clipboardOverride == null ? Toolkit.getDefaultToolkit().getSystemClipboard() : clipboardOverride; }

    /** Notifies the workspace after a move or rename so open buffers can follow their files. */
    public void setMoveListener(java.util.function.BiConsumer<Path, Path> listener) { moveListener = listener == null ? (from, to) -> {} : listener; }

    /** Sets the callback notified when the project directory changes. */
    public void setDirectoryListener(Consumer<Path> listener) { directoryListener = listener == null ? path -> {} : listener; }
    /** Sets the callback notified when a file is deleted from this panel. */
    public void setDeletionListener(Consumer<Path> listener) { deletionListener = listener == null ? path -> {} : listener; }

    /** Opens a folder chooser and switches the project browser root. */
    public void chooseProjectFolder() {
        Path chosen = chooseWindowsFolder(projectDirectory);
        if (chosen != null) setProjectDirectory(chosen, true);
    }

    private Path chooseWindowsFolder(Path initial) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return chooseWithJavaDialog(initial);
        try {
            String start = initial == null ? System.getProperty("user.dir") : initial.toAbsolutePath().normalize().toString();
            String script = "$s=New-Object -ComObject Shell.Application;$f=$s.BrowseForFolder(0,'Select Vulcan project folder',0,'" + psEscape(start) + "');if($f){$f.Self.Path}";
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-STA", "-Command", script).redirectErrorStream(true).start();
            String result = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            process.waitFor();
            if (!result.isBlank()) return Path.of(result).toAbsolutePath().normalize();
        } catch (Exception ignored) { }
        return chooseWithJavaDialog(initial);
    }

    private String psEscape(String text) { return text.replace("'", "''"); }

    private Path chooseWithJavaDialog(Path initial) {
        JFileChooser chooser = new JFileChooser(initial == null ? null : initial.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile().toPath().toAbsolutePath().normalize() : null;
    }

    /** Expands the project tree to reveal a path that belongs to its root. */
    public void openIfNeeded(Path path) {
        if (path == null) return;
        Path target = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(target)) target = target.getParent();
        if (target == null || projectDirectory == null || !target.startsWith(projectDirectory)) return;
        expandTo(target);
    }

    private void setProjectDirectory(Path directory, boolean notify) {
        try {
            Path absolute = directory.toAbsolutePath().normalize();
            if (!Files.isDirectory(absolute)) return;
            projectDirectory = absolute;
            backButton.setEnabled(projectDirectory.getParent() != null);
            settings.setCurrentDirectory(projectDirectory);
            pathLabel.setText(projectDirectory.toString());
            pathLabel.setToolTipText(projectDirectory.toString());
            rootNode.setUserObject(projectDirectory.toFile());
            loadChildren(rootNode);
            model.reload();
            TreePath root = new TreePath(rootNode.getPath());
            tree.expandPath(root);
            if (notify) directoryListener.accept(projectDirectory);
        } catch (Exception error) {
            VulcanDialog.message(this, settings, "Open Project Folder", VulcanDialog.errorSummary(error));
        }
    }

    private void expandTo(Path target) {
        try {
            if (target.equals(projectDirectory)) return;
            Path relative = projectDirectory.relativize(target);
            DefaultMutableTreeNode node = rootNode;
            Path current = projectDirectory;
            for (Path part : relative) {
                current = current.resolve(part);
                loadChildren(node);
                node = findChild(node, current);
                if (node == null) return;
                tree.expandPath(new TreePath(node.getPath()));
            }
        } catch (Exception ignored) { }
    }

    private DefaultMutableTreeNode findChild(DefaultMutableTreeNode parent, Path path) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof File file && file.toPath().toAbsolutePath().normalize().equals(path)) return child;
        }
        return null;
    }

    private void loadChildren(DefaultMutableTreeNode node) {
        Object value = node.getUserObject();
        if (!(value instanceof File directory) || !isTreeDirectory(directory)) return;
        node.removeAllChildren();
        File[] files = directory.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::isFile).thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) node.add(new DefaultMutableTreeNode(file));
        if (files.length == 0) node.add(new DefaultMutableTreeNode());
        model.nodeStructureChanged(node);
    }

    /** Reloads the project root in the file tree. */
    public void refresh() {
        if (projectDirectory == null) return;
        loadChildren(rootNode);
        model.reload(rootNode);
    }

    private void handleZoom(MouseWheelEvent e) {
        if (!e.isControlDown()) {
            JScrollPane scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, tree);
            if (scroll != null) {
                Point point = SwingUtilities.convertPoint(tree, e.getPoint(), scroll);
                scroll.dispatchEvent(new MouseWheelEvent(scroll, e.getID(), e.getWhen(), e.getModifiersEx(),
                        point.x, point.y, e.getXOnScreen(), e.getYOnScreen(), e.getClickCount(), e.isPopupTrigger(),
                        e.getScrollType(), e.getScrollAmount(), e.getWheelRotation(), e.getPreciseWheelRotation()));
                e.consume();
            }
            return;
        }
        e.consume();
        int size = Math.max(8, Math.min(48, renderer.getFontSize() - e.getWheelRotation()));
        if (size != renderer.getFontSize()) {
            renderer.setFontSize(size);
            settings.setFontSize(fontKey, size);
            tree.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, size));
            tree.setRowHeight(Math.max(18, size + 9));
            tree.revalidate();
            tree.repaint();
        }
    }

    private DefaultMutableTreeNode nodeAt(MouseEvent e) {
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        return path == null ? null : (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    private Path selectedDirectory() {
        TreePath selected = tree.getSelectionPath();
        if (selected != null) {
            Object value = ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
            if (value instanceof File f && isTreeDirectory(f)) return f.toPath();
            if (value instanceof File f) return f.toPath().getParent();
        }
        return projectDirectory;
    }

    private void showPopup(MouseEvent e) {
        DefaultMutableTreeNode node = nodeAt(e);
        if (node != null) {
            TreePath clickedPath = new TreePath(node.getPath());
            if (!tree.isPathSelected(clickedPath)) tree.setSelectionPath(clickedPath);
        }
        Path selected = selectedPath();
        Path directory = selected != null && isTreeDirectory(selected.toFile()) ? selected : (selected == null ? projectDirectory : selected.getParent());
        if (directory == null) directory = projectDirectory;
        final Path targetDirectory = directory;

        JPopupMenu menu = new JPopupMenu();
        JMenuItem createFile = new JMenuItem("Create File");
        JMenuItem createFolder = new JMenuItem("Create Folder");
        JMenuItem rename = new JMenuItem("Rename");
        JMenuItem copy = new JMenuItem("Copy");
        JMenuItem cut = new JMenuItem("Cut");
        JMenuItem paste = new JMenuItem("Paste");
        JMenuItem delete = new JMenuItem("Delete");
        JMenuItem duplicate = new JMenuItem("Duplicate");
        JMenuItem undo = new JMenuItem("Undo File Operation");
        JMenuItem openExplorer = new JMenuItem("Open in File Explorer");
        createFile.addActionListener(x -> createFile(targetDirectory));
        createFolder.addActionListener(x -> createFolder(targetDirectory));
        rename.addActionListener(x -> renameSelected());
        copy.addActionListener(x -> copySelectedFiles(false));
        cut.addActionListener(x -> copySelectedFiles(true));
        paste.addActionListener(x -> pasteClipboard(targetDirectory));
        delete.addActionListener(x -> deleteSelected());
        duplicate.addActionListener(x -> duplicateSelected());
        undo.addActionListener(x -> undoFileOperation());
        undo.setEnabled(!fileHistory.isEmpty());
        openExplorer.addActionListener(x -> openInExplorer());
        menu.add(createFile);
        menu.add(createFolder);
        List<Path> selectedItems = selectedPaths();
        if (selectedItems.size() == 1) menu.add(rename);
        if (!selectedItems.isEmpty()) {
            menu.add(copy);
            menu.add(cut);
            menu.add(duplicate);
            menu.add(delete);
        }
        menu.add(paste);
        menu.add(undo);
        menu.addSeparator();
        menu.add(openExplorer);
        menu.show(tree, e.getX(), e.getY());
    }

    private void createFile(Path dir) {
        String name = VulcanDialog.prompt(this, settings, "Create File", "File name:", "");
        if (name == null || name.isBlank()) return;
        try { Path path = Files.createFile(dir.resolve(name.trim())); fileHistory.record(List.of(ProjectFileOperations.Change.created(path))); refresh(); } catch (Exception ex) { VulcanDialog.message(this, settings, "Create File", VulcanDialog.errorSummary(ex)); }
    }

    private void createFolder(Path dir) {
        String name = VulcanDialog.prompt(this, settings, "Create Folder", "Folder name:", "");
        if (name == null || name.isBlank()) return;
        try { Path path = Files.createDirectory(dir.resolve(name.trim())); fileHistory.record(List.of(ProjectFileOperations.Change.created(path))); refresh(); } catch (Exception ex) { VulcanDialog.message(this, settings, "Create Folder", VulcanDialog.errorSummary(ex)); }
    }

    private void renameSelected() {
        List<Path> selection = selectedPaths();
        if (selection.size() != 1) return;
        Path selected = selection.get(0);
        String name = VulcanDialog.prompt(this, settings, "Rename", "New name:", selected.getFileName().toString());
        if (name == null || name.isBlank()) return;
        try {
            Path destination = selected.resolveSibling(name.trim());
            if (selected.equals(destination)) return;
            ProjectFileOperations.moveNew(selected, destination);
            fileHistory.record(List.of(ProjectFileOperations.Change.moved(selected, destination)));
            moveListener.accept(selected, destination);
            refresh();
        } catch (Exception ex) { VulcanDialog.message(this, settings, "Rename", VulcanDialog.errorSummary(ex)); }
    }

    private void pasteClipboard(Path directory) {
        try {
            Transferable contents = clipboard().getContents(null);
            if (contents == null || !contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return;
            Object metadata = contents.isDataFlavorSupported(FILE_OPERATION) ? contents.getTransferData(FILE_OPERATION) : null;
            ClipboardOperation operation = metadata instanceof ClipboardOperation value ? value : null;
            if (operation != null && operation.cut && operation.consumed) return;
            Object data = contents.getTransferData(DataFlavor.javaFileListFlavor);
            if (!(data instanceof List<?> list)) return;
            List<Path> sources = new java.util.ArrayList<>();
            for (Object item : list) if (item instanceof File file) sources.add(file.toPath().toAbsolutePath().normalize());
            boolean move = operation != null && operation.cut;
            if (transferEntries(sources, directory, move, false) && move) operation.consumed = true;
        } catch (Exception ex) { VulcanDialog.message(this, settings, "Paste", VulcanDialog.errorSummary(ex)); }
    }

    /** Copies or moves a batch without overwriting entries and records completed work for Ctrl-Z. */
    private boolean transferEntries(List<Path> sources, Path directory, boolean move, boolean duplicate) {
        List<ProjectFileOperations.Change> changes = new java.util.ArrayList<>();
        List<String> errors = new java.util.ArrayList<>();
        List<Path> destinations = new java.util.ArrayList<>();
        for (Path entry : sources) {
            Path source = entry.toAbsolutePath().normalize();
            Path folder = duplicate ? source.getParent() : directory.toAbsolutePath().normalize();
            if (move && folder.resolve(source.getFileName()).equals(source)) continue;
            try {
                if (folder.startsWith(source)) throw new IOException("A folder cannot be pasted into itself.");
                Path destination = ProjectFileOperations.uniqueTarget(source, folder, duplicate);
                if (move) {
                    ProjectFileOperations.moveNew(source, destination);
                    changes.add(ProjectFileOperations.Change.moved(source, destination));
                    moveListener.accept(source, destination);
                } else {
                    ProjectFileOperations.copyNew(source, destination);
                    changes.add(ProjectFileOperations.Change.created(destination));
                }
                destinations.add(destination);
            } catch (IOException error) { errors.add(source.getFileName() + ": " + VulcanDialog.errorSummary(error)); }
        }
        fileHistory.record(changes);
        refresh();
        selectEntries(destinations);
        if (!errors.isEmpty()) VulcanDialog.message(this, settings, move ? "Move" : "Copy", String.join("\n", errors));
        return errors.isEmpty();
    }

    /** Creates uniquely numbered copies of the selected entries in their current folders. */
    private void duplicateSelected() { transferEntries(selectedPaths(), projectDirectory, false, true); }

    /** Reverses the last completed file operation without overwriting or deleting newer data. */
    private void undoFileOperation() {
        try {
            List<ProjectFileOperations.Change> undone = fileHistory.undo(change -> {
                if (change.original() != null) moveListener.accept(change.destination(), change.original());
            });
            refresh();
            selectEntries(undone.stream().map(ProjectFileOperations.Change::original).filter(java.util.Objects::nonNull).toList());
        } catch (IOException error) { VulcanDialog.message(this, settings, "Undo", VulcanDialog.errorSummary(error)); }
    }

    private void selectEntries(List<Path> paths) {
        List<TreePath> selection = new java.util.ArrayList<>();
        tree.expandPath(new TreePath(rootNode.getPath()));
        for (Path path : paths) {
            if (!path.startsWith(projectDirectory)) continue;
            expandTo(path.getParent());
            for (int row = 0; row < tree.getRowCount(); row++) {
                TreePath candidate = tree.getPathForRow(row);
                Object value = ((DefaultMutableTreeNode) candidate.getLastPathComponent()).getUserObject();
                if (value instanceof File file && file.toPath().toAbsolutePath().normalize().equals(path)) selection.add(candidate);
            }
        }
        tree.setSelectionPaths(selection.toArray(TreePath[]::new));
    }

    private void deleteSelected() {
        List<Path> selected = selectedPaths();
        if (selected.isEmpty()) return;
        String description = selected.size() == 1 ? "Delete " + selected.get(0).getFileName() + "?"
                : "Delete these " + selected.size() + " items?";
        if (!VulcanDialog.confirm(this, settings, "Delete", description)) return;
        List<String> failures = new java.util.ArrayList<>();
        List<ProjectFileOperations.Change> changes = new java.util.ArrayList<>();
        Path backup;
        try {
            Path storage = settings.getPath().getParent().resolve("file-undo");
            Files.createDirectories(storage);
            backup = Files.createTempDirectory(storage, "deleted-");
        } catch (IOException error) { VulcanDialog.message(this, settings, "Delete", VulcanDialog.errorSummary(error)); return; }
        for (Path path : selected) {
            try {
                Path stored = backup.resolve(changes.size() + "-" + path.getFileName());
                ProjectFileOperations.moveNew(path, stored);
                changes.add(ProjectFileOperations.Change.moved(path, stored));
                deletionListener.accept(path.toAbsolutePath().normalize());
            } catch (Exception ex) {
                failures.add(path.getFileName() + ": " + VulcanDialog.errorSummary(ex));
            }
        }
        fileHistory.record(changes);
        refresh();
        if (!failures.isEmpty()) VulcanDialog.message(this, settings, "Delete", String.join("\n", failures));
    }

    /** Copies or marks all selected project entries for a later move. */
    private void copySelectedFiles(boolean cut) {
        List<Path> selected = selectedPaths();
        if (selected.isEmpty()) return;
        List<File> files = selected.stream().map(Path::toFile).toList();
        try {
            clipboard().setContents(new FileListTransferable(files, new ClipboardOperation(cut)), null);
        } catch (Exception error) {
            VulcanDialog.message(this, settings, cut ? "Cut" : "Copy", VulcanDialog.errorSummary(error));
        }
    }

    /** Returns selected entries without duplicate descendants or the project root. */
    private List<Path> selectedPaths() {
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) return List.of();
        List<Path> candidates = new java.util.ArrayList<>();
        for (TreePath treePath : paths) {
            Object value = ((DefaultMutableTreeNode) treePath.getLastPathComponent()).getUserObject();
            if (!(value instanceof File file)) continue;
            Path path = file.toPath().toAbsolutePath().normalize();
            if (!path.equals(projectDirectory) && path.startsWith(projectDirectory) && !candidates.contains(path)) candidates.add(path);
        }
        candidates.sort(Comparator.comparingInt(Path::getNameCount));
        List<Path> roots = new java.util.ArrayList<>();
        for (Path candidate : candidates) {
            boolean covered = roots.stream().anyMatch(candidate::startsWith);
            if (!covered) roots.add(candidate);
        }
        return List.copyOf(roots);
    }

    /** Exposes selected paths to the operating system as both files and text. */
    private static final class FileListTransferable implements Transferable {
        private final List<File> files;
        private final String paths;
        private final ClipboardOperation operation;

        private FileListTransferable(List<File> files, ClipboardOperation operation) {
            this.files = List.copyOf(files);
            this.paths = String.join(System.lineSeparator(), files.stream().map(File::getAbsolutePath).toList());
            this.operation = operation;
        }

        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor, FILE_OPERATION};
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return flavor.equals(DataFlavor.javaFileListFlavor) || flavor.equals(DataFlavor.stringFlavor) || flavor.equals(FILE_OPERATION);
        }

        @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(DataFlavor.javaFileListFlavor)) return files;
            if (flavor.equals(DataFlavor.stringFlavor)) return paths;
            if (flavor.equals(FILE_OPERATION)) return operation;
            throw new UnsupportedFlavorException(flavor);
        }
    }

    private static final class ClipboardOperation {
        private final boolean cut;
        private boolean consumed;
        private ClipboardOperation(boolean cut) { this.cut = cut; }
    }

    private void copyPath() {
        Path selected = selectedPath();
        if (selected == null || !Files.exists(selected)) return;
        final Path copied = selected;
        try {
            clipboard().setContents(new StringSelection(copied.toString()), null);
        } catch (Exception error) {
            VulcanDialog.message(this, settings, "Copy Path", VulcanDialog.errorSummary(error));
        }
    }

    private Path selectedPath() {
        TreePath selected = tree.getSelectionPath();
        if (selected == null) return projectDirectory;
        Object value = ((DefaultMutableTreeNode) selected.getLastPathComponent()).getUserObject();
        return value instanceof File f ? f.toPath().toAbsolutePath().normalize() : projectDirectory;
    }

    private static boolean isTreeDirectory(File file) {
        Path path = file.toPath();
        return !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    private void openInExplorer() {
        Path path = selectedPath();
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", "/e,/root," + path.toString()).start();
            } else if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(path.toFile());
        } catch (Exception error) {
            VulcanDialog.message(this, settings, "Open in Explorer", VulcanDialog.errorSummary(error));
        }
    }

    /** Applies the current theme colors and refreshes this component. */
    public void refreshTheme() {
        setBackground(settings.getColor("panelBackground"));
        title.setForeground(settings.getColor("text"));
        pathLabel.setForeground(settings.getColor("tabTextMuted"));
        title.setFont(new Font(settings.getApplicationFont(), Font.BOLD, settings.getFontSize(fontKey + "Title")));
        pathLabel.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize(fontKey + "Path")));
        tree.setFont(new Font(settings.getApplicationFont(), Font.PLAIN, settings.getFontSize(fontKey)));
        renderer.setFontSize(settings.getFontSize(fontKey));
        tree.setRowHeight(Math.max(18, settings.getFontSize(fontKey) + 9));
        renderer.updateColors();
        backButton.setIcon(Icons.vector("back", 16, settings.getColor("text")));
        tree.repaint();
        repaint();
    }

    private final class FileDropHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport support) { return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                @SuppressWarnings("unchecked") List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                Path target = selectedDirectory();
                return transferEntries(files.stream().map(File::toPath).toList(), target, false, false);
            } catch (Exception error) {
                VulcanDialog.message(FileSystemPanel.this, settings, "Paste", VulcanDialog.errorSummary(error));
                return false;
            }
        }
    }

    /** Returns the root directory currently shown in the project browser. */
    public Path getProjectDirectory() { return projectDirectory; }

    private static final class LocalFileRenderer extends DefaultTreeCellRenderer {
        private final SettingsManager settings;
        private final String fontKey;
        private int fontSize;
        LocalFileRenderer(SettingsManager settings, String fontKey) {
            this.settings = settings;
            this.fontKey = fontKey;
            this.fontSize = settings.getFontSize(fontKey);
            updateColors();
        }
        int getFontSize() { return fontSize; }
        void setFontSize(int size) { fontSize = size; }
        void updateColors() {
            setBackgroundSelectionColor(settings.getColor("fileSelectionBackground"));
            setTextSelectionColor(settings.getColor("fileSelectionText"));
            setBackgroundNonSelectionColor(settings.getColor("inputBackground"));
            setTextNonSelectionColor(settings.getColor("text"));
            setBorderSelectionColor(settings.getColor("fileSelectionBackground"));
        }
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            setFont(new Font(settings.getApplicationFont(), Font.PLAIN, fontSize));
            setIcon(null);
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof File file) {
                setText(file.getName());
                int iconSize = Math.max(12, fontSize + 2);
                if (isTreeDirectory(file)) setIcon(Icons.fit(expanded ? "dir-op-icon.png" : "dir-icon.png", iconSize, iconSize));
                else {
                    setIcon(Icons.file(file, iconSize));
                }
            }
            return this;
        }
    }
}
