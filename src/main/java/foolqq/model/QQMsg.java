package foolqq.model;

/**
 * Immutable message object.
 */
public class QQMsg {

	private String nick;
	
	private String qqOrEmail;
	
	private String time;
	
	private String content = "";

	public QQMsg(String nick, String qqOrEmail, String time, String content) {
		this.nick = nick;
		this.qqOrEmail = qqOrEmail;
		this.time = time;
		this.content = content;
	}

	public QQMsg(){

	}

	public String getNick() {
		return nick;
	}

	public String getQqOrEmail() {
		return qqOrEmail;
	}


	public String getTime() {
		return time;
	}

	public String getContent() {
		return content;
	}

	@Override
	public String toString() {
		return "QQMsg [nick=" + nick + ", qqOrEmail=" + qqOrEmail + ", time=" + time + ", content=" + content + "]";
	}

}
