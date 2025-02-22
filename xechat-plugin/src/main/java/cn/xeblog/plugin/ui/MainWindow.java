package cn.xeblog.plugin.ui;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.xeblog.commons.entity.UserMsgDTO;
import cn.xeblog.commons.enums.Action;
import cn.xeblog.plugin.action.ConsoleAction;
import cn.xeblog.plugin.action.MessageAction;
import cn.xeblog.plugin.cache.DataCache;
import cn.xeblog.plugin.enums.Command;
import cn.xeblog.plugin.util.CommandHistoryUtils;
import cn.xeblog.plugin.util.UploadUtils;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author anlingyi
 * @date 2020/5/26
 */
public class MainWindow {
    private JPanel mainPanel;
    private JTextPane console;
    private JTextArea contentArea;
    private JPanel leftPanel;
    private JPanel rightPanel;
    private JPanel contentPanel;
    private JScrollPane consoleScroll;
    private JPanel leftTopPanel;

    private static long lastSendTime;

    private JBList jbList = null;

    private boolean isProactive;

    private MainWindow() {
        init();
    }

    private void init() {
        ConsoleAction.setConsole(console);
        ConsoleAction.setPanel(leftPanel);
        ConsoleAction.setConsoleScroll(consoleScroll);

        Command.HELP.exec(null);

        contentArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                String content = contentArea.getText();

                if (KeyEvent.VK_ENTER == e.getKeyCode()) {
                    // 阻止默认事件
                    e.consume();
                    sendMsg();
                }

                if (e.getKeyCode() == KeyEvent.VK_TAB && leftTopPanel.isVisible()) {
                    e.consume();
                }

                if (e.getKeyCode() == 38 || e.getKeyCode() == 40) {
                    e.consume();
                    if (isProactive && leftTopPanel.isVisible() && jbList != null) {
                        jbList.requestFocus();
                    } else if (StrUtil.isBlank(content) || content.startsWith(Command.COMMAND_PREFIX)) {
                        String cmd = null;
                        if (e.getKeyCode() == 38) {
                            cmd = CommandHistoryUtils.getPrevCommand();
                        } else if (e.getKeyCode() == 40) {
                            cmd = CommandHistoryUtils.getNextCommand();
                        }

                        if (StrUtil.isNotBlank(cmd)) {
                            isProactive = false;
                            contentArea.setText(cmd);
                        }
                    }
                } else {
                    isProactive = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if ((e.isControlDown() || e.isMetaDown()) && e.getKeyCode() == KeyEvent.VK_V) {
                    if (!DataCache.isOnline) {
                        ConsoleAction.showLoginMsg();
                        return;
                    }

                    // 粘贴图片
                    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                    Transferable transferable = clipboard.getContents(null);
                    try {
                        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            List<File> fileList = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                            UploadUtils.uploadImageFile(fileList.get(0));
                            cleanContent();
                        } else if (transferable.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                            Image image = (Image) transferable.getTransferData(DataFlavor.imageFlavor);
                            UploadUtils.uploadImage(image);
                            cleanContent();
                        }
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                } else {
                    boolean isAt = false;
                    List<String> dataList = null;
                    String content = contentArea.getText();
                    int caretPosition = contentArea.getCaretPosition();
                    int atIndex = -1;
                    String commandPrefix = Command.COMMAND_PREFIX;
                    if (content.startsWith(commandPrefix)) {
                        Map<String, String> commandMap = new LinkedHashMap<>();
                        for (Command command : Command.values()) {
                            commandMap.put(command.getCommand(), command.getCommand() + " (" + command.getDesc() + ")");
                        }

                        String command = content;
                        if (StrUtil.isBlank(command)) {
                            dataList = new ArrayList<>(commandMap.values());
                        } else {
                            final List<String> matchList = new ArrayList<>();
                            commandMap.forEach((k, v) -> {
                                if (k.toLowerCase().contains(command.toLowerCase()) || command.startsWith(k)) {
                                    matchList.add(v);
                                }
                            });
                            dataList = matchList;
                        }
                    } else {
                        if (DataCache.isOnline) {
                            isAt = true;
                            String atContent = content.substring(0, caretPosition);
                            atIndex = atContent.lastIndexOf("@");
                            if (atIndex > -1) {
                                String name = content.substring(atIndex + 1, caretPosition);
                                List<String> allUserList = new ArrayList<>(DataCache.userMap.keySet());

                                if (atIndex + 1 == caretPosition) {
                                    dataList = allUserList;
                                }

                                if (StrUtil.isNotBlank(name)) {
                                    dataList = new ArrayList<>();
                                    for (String user : allUserList) {
                                        if (user.toLowerCase().contains(name.toLowerCase())) {
                                            dataList.add(user);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    leftTopPanel.setVisible(false);
                    leftTopPanel.removeAll();

                    if (CollectionUtil.isNotEmpty(dataList)) {
                        boolean copyIsAt = isAt;
                        int copyAtIndex = atIndex;

                        Runnable runnable = () -> {
                            String value = jbList.getSelectedValue().toString();
                            if (copyIsAt) {
                                contentArea.replaceRange(value + " ", copyAtIndex + 1, caretPosition);
                            } else {
                                contentArea.setText(value.substring(0, value.indexOf(" ")));
                            }

                            contentArea.requestFocus();
                            leftTopPanel.setVisible(false);
                            leftTopPanel.removeAll();
                        };

                        jbList = new JBList();
                        jbList.setListData(dataList.toArray());
                        jbList.addKeyListener(new KeyAdapter() {
                            @Override
                            public void keyPressed(KeyEvent e) {
                                if (KeyEvent.VK_ENTER == e.getKeyCode()) {
                                    runnable.run();
                                }
                            }
                        });

                        jbList.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                if (e.getClickCount() == 2) {
                                    runnable.run();
                                }
                            }
                        });

                        JBScrollPane scrollPane = new JBScrollPane(jbList);
                        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

                        leftTopPanel.setMinimumSize(new Dimension(0, 100));
                        leftTopPanel.add(scrollPane);
                        leftTopPanel.setVisible(true);

                        if (e.getKeyCode() == KeyEvent.VK_TAB) {
                            String value = dataList.get(0);
                            if (copyIsAt) {
                                contentArea.replaceRange(value + " ", copyAtIndex + 1, caretPosition);
                            } else {
                                contentArea.replaceRange(value.substring(0, value.indexOf(" ")), 0, caretPosition);
                            }
                        }
                    }

                    leftTopPanel.updateUI();
                }
            }
        });
    }

    private static final MainWindow MAIN_WINDOW = new MainWindow();

    public static MainWindow getInstance() {
        return MAIN_WINDOW;
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public JPanel getRightPanel() {
        return rightPanel;
    }

    private void sendMsg() {
        String content = contentArea.getText();
        if (StringUtils.isEmpty(content)) {
            return;
        }

        if (content.length() > 500) {
            ConsoleAction.showSimpleMsg("发送的内容长度不能超过500字符！");
        } else {
            if (content.startsWith(Command.COMMAND_PREFIX)) {
                ConsoleAction.showSimpleMsg(content);
                Command.handle(content);
            } else {
                if (DataCache.isOnline) {
                    long sendTime = System.currentTimeMillis();
                    if (lastSendTime + 800 > sendTime) {
                        ConsoleAction.showSimpleMsg("休息一下哦~");
                        return;
                    }

                    lastSendTime = sendTime;
                    String[] toUsers = null;
                    List<String> toUserList = ReUtil.findAll("(@)([^\\s]+)([\\s]*)", content, 2);
                    if (CollectionUtil.isNotEmpty(toUserList)) {
                        List<String> removeList = new ArrayList<>();
                        for (String toUser : toUserList) {
                            if (DataCache.getUser(toUser) == null) {
                                removeList.add(toUser);
                            }
                        }
                        if (!removeList.isEmpty()) {
                            toUserList.removeAll(removeList);
                        }
                        if (!toUserList.isEmpty()) {
                            toUserList.add(DataCache.username);
                            toUsers = ArrayUtil.toArray(new HashSet<>(toUserList), String.class);
                        }
                    }
                    MessageAction.send(new UserMsgDTO(content, toUsers), Action.CHAT);
                } else {
                    ConsoleAction.showLoginMsg();
                }
            }
            cleanContent();
        }

        ConsoleAction.gotoConsoleLow();
    }

    private void cleanContent() {
        contentArea.setText("");
    }

}
