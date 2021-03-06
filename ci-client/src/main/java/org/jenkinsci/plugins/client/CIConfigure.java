package org.jenkinsci.plugins.client;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.BaseElement;
import org.jenkinsci.plugins.client.build.JenkinsConfig;
import org.jenkinsci.plugins.client.subversion.SVNAuth;
import org.jenkinsci.plugins.client.windows.MessageWindow;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

public class CIConfigure {
	private static CIConfigure _instance;
	/** 临时数据存储路径 */
	public static final String DATA_PATH = System.getenv("HOME")+File.separator + "AppData/Local/ci-data" + File.separator;
	
	/** Jenkins 地址：http://10.95.121.180:8084/jenkins/ */
	private static final String JENKINS_URL = "http://10.95.121.181:8081/jenkins/";
	/** 配置文件路径 */
	public static final String CONFIG_PATH = DATA_PATH + "config.xml";
	
	
	private CIConfigure(){}
	
	public static CIConfigure getInstance() {
		if ( _instance==null ) {
			_instance = new CIConfigure();
			File dataPath = new File(DATA_PATH);
			if ( !dataPath.exists() ) {
				dataPath.mkdirs();
			}
			File xml = new File(CONFIG_PATH);
			if ( !xml.exists() ) {
				_instance.initConfig();
			}
		}
		return _instance;
	}
	/**
	 * 
	 * <p>Discription:[初始化配置文件]</p>
	 * Created on 2017年9月26日
	 * @author[hanshixiong]
	 */
	private void initConfig() {
		Writer writer = null;
		try {
			Document doc = DocumentHelper.createDocument();
			Element rootEle = new BaseElement("ci-config");
			
			// svn auth
			Element svnEle = new BaseElement("svn-auth");
			svnEle.add(new BaseElement("username"));
			svnEle.add(new BaseElement("password"));
			
			// jenkins
			Element jenkinsEle = new BaseElement("jenkins");
			Element rootUrlEle = new BaseElement("root-url");
			rootUrlEle.setText(JENKINS_URL);
			svnEle.add(rootUrlEle);
			
			rootEle.add(svnEle);
			rootEle.add(jenkinsEle);
			
			doc.add(rootEle);
			writer = new PrintWriter(CONFIG_PATH);
			doc.write(writer);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			if ( writer!=null ) {
				try {
					writer.close();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		}
	}
	/**
	 * 
	 * <p>Discription:[设置SVN账号密码]</p>
	 * Created on 2017年9月26日
	 * @param username
	 * @param password
	 * @author[hanshixiong]
	 */
	public void setSVNAuth(String username, String password) {
		// 更新SVN认证信息
		CIApplication.patchHelper.setSVN(username, password);
		SAXReader saxReader = new SAXReader();
		try {
			Document doc = saxReader.read(CONFIG_PATH);
			Element rootEle = doc.getRootElement();
			Element nameEle = rootEle.element("svn-auth").element("username");
			nameEle.setText(username);
			Element passwordEle = rootEle.element("svn-auth").element("password");
			passwordEle.setText(password);
			Writer writer = new PrintWriter(CONFIG_PATH);
			doc.write(writer);
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
			resetConfig();
		} 
	}
	/**
	 * 
	 * <p>Discription:[获取SVN认证信息]</p>
	 * Created on 2017年9月26日
	 * @return
	 * @author[hanshixiong]
	 */
	public SVNAuth getSVNAuth() {
		SAXReader saxReader = new SAXReader();
		Document doc;
		try {
			doc = saxReader.read(CONFIG_PATH);
			Element rootEle = doc.getRootElement();
			Element nameEle = rootEle.element("svn-auth").element("username");
			String username = nameEle.getText();
			Element passwordEle = rootEle.element("svn-auth").element("password");
			String password = passwordEle.getText();
			SVNAuth svnAuth = new SVNAuth();
			svnAuth.setUsername(username);
			svnAuth.setPassword(password);
			return svnAuth;
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 
	 * <p>Discription:[获取Jenkins配置信息]</p>
	 * Created on 2017年10月29日
	 * @return
	 * @author[hanshixiong]
	 */
	public JenkinsConfig getJenkins() {
		
		JenkinsConfig config = new JenkinsConfig();
		
		SAXReader saxReader = new SAXReader();
		Document doc;
		try {
			doc = saxReader.read(CONFIG_PATH);
			Element rootEle = doc.getRootElement();
			Element rootUrlEle = rootEle.element("jenkins").element("root-url");
			String rootUrl = rootUrlEle.getText();
			config.setRootUrl(rootUrl);
			return config;
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		
		return null;
		
	}
	/**
	 * 
	 * <p>Discription:[检查SVN配置信息]</p>
	 * Created on 2017年9月26日
	 * @return 已配置返回true未配置返回false
	 * @author[hanshixiong]
	 */
	public boolean isSVNEffective() {
		SVNAuth svnAuthInfo = new SVNAuth();
		if ( !isSVNConfigEffective(svnAuthInfo) ) {
			return false;
		}
		return isSVNAuthEffective(svnAuthInfo);
	}
	/**
	 * 
	 * <p>Discription:[检查认证信息是否正确]</p>
	 * Created on 2017年9月30日
	 * @param svnAuthInfo
	 * @return
	 * @author[hanshixiong]
	 */
	private boolean isSVNAuthEffective(SVNAuth svnAuthInfo) {
		try {
			@SuppressWarnings("deprecation")
			ISVNAuthenticationManager svnAuth = new BasicAuthenticationManager( svnAuthInfo.getUsername() , svnAuthInfo.getPassword() );
			SVNClientManager svnClientManager = SVNClientManager.newInstance(null,svnAuth);
			svnClientManager.setAuthenticationManager(svnAuth);
			System.out.println(CIApplication.mainArguments.getWorkspacePath());
			svnClientManager.getWCClient().doInfo(new File(CIApplication.mainArguments.getWorkspacePath()),SVNRevision.HEAD);
		} catch (SVNException e) {
			e.printStackTrace();
			// SVN认证失败，重新录入SVN账号
			if ( e.getMessage().contains("E170001") ) {
				return false;
			} else { // 其他SVN异常直接提示退出
				MessageWindow.info(MessageWindow.TitleEnum.ERROR, "SVN认证失败", MessageWindow.ExitEnum.FAILURE);
			}
		}
		return true;
	}
	
	/**
	 * 
	 * <p>Discription:[检查配置文件中是否已存SVN认证信息]</p>
	 * Created on 2017年9月30日
	 * @param svnAuth
	 * @return
	 * @author[hanshixiong]
	 */
	private boolean isSVNConfigEffective(SVNAuth svnAuth) {
		String name;
		String password;
		SAXReader saxReader = new SAXReader();
		try {
			Document doc = saxReader.read(CONFIG_PATH);
			Element rootEle = doc.getRootElement();
			Element nameEle = rootEle.element("svn-auth").element("username");
			name = nameEle.getText();
			Element passwordEle = rootEle.element("svn-auth").element("password");
			password = passwordEle.getText();
			if ( name==null || password==null || name.trim().isEmpty() || password.trim().isEmpty() ) {
				return false;
			}
			svnAuth.setPassword(password);
			svnAuth.setUsername(name);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	/**
	 * 
	 * <p>Discription:[方法功能中文描述]</p>
	 * Created on 2017年9月26日
	 * @author[hanshixiong]
	 */
	private void resetConfig() {
		File config = new File(CONFIG_PATH);
		config.delete();
		this.initConfig();
	}
}
