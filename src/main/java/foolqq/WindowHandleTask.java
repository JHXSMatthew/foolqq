package foolqq;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

import foolqq.BaseQQWindowContext;
import foolqq.model.QQMsg;
import foolqq.model.QQMsgBuilder;
import foolqq.model.QQWindow;
import static foolqq.tool.QQWindowTool.*;

public class WindowHandleTask implements Runnable {

	private BaseQQWindowContext context;


	public WindowHandleTask(BaseQQWindowContext context) {
		this.context = context;
		updateMap();
	}

	@Override
	public void run() {
		synchronized (context) {
			// 1.update map(location of QQWindow)
			// 2.readMessage and call onMessage
			updateMap();

			context.onReadMessage();
		}
	}

	/**
	 * must used in synchronized block or method on context
	 */
	private void updateMap(){
		BufferedImage image = getScreen(context.getRobot());
		int width = image.getWidth();
		int height = image.getHeight();
		if(isUniColor(image)) {
			// NOTE: Robot capture a blackScreen without desktop access.
			// It might cause problem that bot keeps an empty map!
			return;
		}else{
			Map<String,QQWindow> map = new HashMap<>();
			File[] f = new File(".").listFiles();
			for (int i = 0; i < f.length; i++) {
				if (f[i].getName().endsWith(".png") && !f[i].getName().equals("point.png")) {
					BufferedImage img = null;
					try {
						img = ImageIO.read(new File(f[i].getName()));
					} catch (IOException e) {
						e.printStackTrace();
					}

					String name = getImgName(f[i].getName());
					QQWindow win = new QQWindow(name, img);

					for (int x = 10; x < width - 200; ++x) {
						for (int y = 10; y < height - 200; ++y) {
							if (isEqual(x, y, image, img)) {
								win.setX(x);
								win.setY(y);
								break;
							}
						}
					}

					if (win.getX() == 0 || win.getY() == 0)
						continue;

					map.put(name, win);
				}
			}
			context.onQQWindowUpdate(image,map);
		}
	}



}
