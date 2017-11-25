package foolqq;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import foolqq.exception.CannotFindWindowException;
import foolqq.exception.ScreenCaptureException;
import foolqq.model.QQMsgBuilder;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;
import foolqq.listener.GlobalKeyListener;
import foolqq.model.QQMsg;
import foolqq.model.QQWindow;

import static foolqq.tool.ClipboardTool.*;
import static foolqq.tool.QQWindowTool.*;

public abstract class BaseQQWindowContext {

	private final static String msgHeadRegExp = "(.*)\\((.+)\\)\\s+([0-9]{1,2}:[0-9]{2}:[0-9]{2})";

	private Map<String, QQWindow> map = new HashMap<String, QQWindow>();

	private Robot robot;

	private int interval = 200;

	private int checkInterval = 5;

	private BufferedImage pImage;

	private BufferedImage screen;

	private ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();

	private WindowHandleTask wintask;

	public BaseQQWindowContext(File point, boolean allowESCQuit) throws AWTException, IOException, NativeHookException {
		init(point, allowESCQuit);

	}

	public BaseQQWindowContext(File point) throws AWTException, IOException, NativeHookException {
		init(point, true);

	}

	private void init(File point, boolean allowESCQuit) throws IOException, AWTException, NativeHookException {
		robot = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice());
		pImage = ImageIO.read(point);
		wintask = new WindowHandleTask(this);
		startScheduler();
		Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
		logger.setLevel(Level.OFF);
		logger.setUseParentHandlers(false);
		GlobalScreen.registerNativeHook();
		if(allowESCQuit){
			GlobalScreen.addNativeKeyListener(new GlobalKeyListener());
		}
	}

	private synchronized void startScheduler(){
		pool.scheduleAtFixedRate(wintask, checkInterval, checkInterval,
				TimeUnit.SECONDS);
	}


	public abstract void onMessage(String name, QQMsg msg);


	/**
	 *
	 * @param name the identifier of QQGroup
	 * @param msg the message to send, will call .toString()
	 */
	public synchronized void writeQQMsg(String name, Object msg) throws ScreenCaptureException, CannotFindWindowException {
		int x = map.get(name).getX();
		int y = map.get(name).getY();
		if (x == 0 || y == 0)
			return;
		boolean found = false;
		int height = getScreen().getHeight();
		for (int i = x - 100; i < x + 200; ++i) {
			for (int j = y + 100; j < height - 100; ++j) {
				if (isEqual(i, j, screen, pImage)) {
					robot.mouseMove(i + 150, j + 50);
					robot.delay(interval);
					robot.mousePress(InputEvent.BUTTON1_MASK);
					robot.mouseRelease(InputEvent.BUTTON1_MASK);
					robot.delay(interval);
					if (msg instanceof String) {
						setSysClipboardText((String) msg);
					} else if (msg instanceof File) {
						Image imgObj = null;
						try {
							imgObj = ImageIO.read((File) msg);
						} catch (IOException e) {
							e.printStackTrace();
						}
						setClipboardImage(imgObj);

					} else if (msg instanceof Image) {
						setClipboardImage((Image) msg);
					}

					robot.keyPress(KeyEvent.VK_CONTROL);
					robot.keyPress(KeyEvent.VK_A);
					robot.keyRelease(KeyEvent.VK_A);
					robot.delay(interval);
					robot.keyPress(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_V);
					robot.keyRelease(KeyEvent.VK_CONTROL);
					robot.delay(interval);
					robot.keyPress(KeyEvent.VK_ENTER);
					robot.keyRelease(KeyEvent.VK_ENTER);
					found = true;
					break;
				}
			}
		}
		if(!found){
			throw new CannotFindWindowException("cannot find window of " + name);
		}
	}

	private synchronized String readQQMsg(String name) {
		int x = map.get(name).getX();
		int y = map.get(name).getY();
		if (x == 0 || y == 0)
			return null;
		if(screen == null){
			return null;
		}
		int height = screen.getHeight();
		for (int i = x - 100; i < x + 200; ++i) {
			for (int j = y + 100; j < height - 100; ++j) {
				if (isEqual(i, j, screen, pImage)) {
					robot.mouseMove(i + 150, j - 100);
					robot.delay(interval);
					robot.mousePress(InputEvent.BUTTON1_MASK);
					robot.mouseRelease(InputEvent.BUTTON1_MASK);
					robot.delay(interval);
					robot.keyPress(KeyEvent.VK_CONTROL);
					robot.keyPress(KeyEvent.VK_A);
					robot.keyRelease(KeyEvent.VK_A);
					robot.delay(interval);
					robot.keyPress(KeyEvent.VK_C);
					robot.keyRelease(KeyEvent.VK_C);
					robot.keyRelease(KeyEvent.VK_CONTROL);
					robot.delay(interval);
					return getSystemClipboard();
				}
			}
		}
		return null;
	}

	private synchronized void clearQQMsg() {
		robot.keyPress(KeyEvent.VK_F10);
		robot.keyRelease(KeyEvent.VK_F10);
	}

	protected synchronized void onQQWindowUpdate(BufferedImage screen,Map<String,QQWindow> map ){
		this.map.clear();
		this.map.putAll(map);
		this.screen = screen;
	}

	protected synchronized void onReadMessage(){
		for(String name : map.keySet()){
			String msg = readQQMsg(name);
			if (msg != null && msg.trim().length() > 0) {
				QQMsg[] stack = getMsgStack(msg.trim().split("\n"));
				for (QQMsg m : stack) {
					onMessage(name, m);
				}

				clearQQMsg();
			}
		}
	}

	private QQMsg[] getMsgStack(String[] msgs) {
		List<QQMsgBuilder> stack = new ArrayList<QQMsgBuilder>();
		for (int j = 0; j < msgs.length; j++) {
			if (msgs[j].matches(msgHeadRegExp)) {
				Pattern pat = Pattern.compile(msgHeadRegExp);
				Matcher mat = pat.matcher(msgs[j]);
				QQMsgBuilder qqMsg = new QQMsgBuilder();
				if (mat.find()) {
					qqMsg.setNick(mat.group(1).trim());
					qqMsg.setQqOrEmail(mat.group(2).trim());
					qqMsg.setTime(mat.group(3).trim());
					stack.add(qqMsg);
				}
			} else {
				if (stack.size() > 0) {
					QQMsgBuilder last = stack.get(stack.size() - 1);
					last.setContent(last.getContent() + msgs[j] + "\n");
				}
			}
		}
		//for java7
		QQMsg[] returnValue = new QQMsg[stack.size()];
		for(int i = 0 ; i < stack.size() ; i ++){
			returnValue[i] = stack.get(i).build();
		}
		return returnValue;
	}

	public int getCheckInterval() {
		return checkInterval;
	}

	/**
	 *
	 * @param checkInterval the new interval
	 */
	public void setCheckInterval(int checkInterval) {
		this.checkInterval = checkInterval;
		startScheduler();
	}

	public void setInterval(int interval) {
		this.interval = interval;
	}

	protected synchronized Robot getRobot(){
		return robot;
	}

	private BufferedImage getScreen() throws ScreenCaptureException {
		if(screen == null){
			throw new ScreenCaptureException("cannot capture the screen.");
		}
		return screen;
	}


}
