package edu.harvard.econcs.turkserver.config;

import java.io.File;
import java.io.FileNotFoundException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;

import com.amazonaws.mturk.util.ClientConfig;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

import edu.harvard.econcs.turkserver.mturk.RequesterServiceExt;
import edu.harvard.econcs.turkserver.server.gui.TSTabbedPanel;

public class DataModule extends AbstractModule {

	protected Configuration conf;	
	
	public DataModule(String path) throws FileNotFoundException, ConfigurationException {				
		File confFile = new File(ServerModule.class.getClassLoader().getResource(path).getFile());
		if( !confFile.exists() ) throw new FileNotFoundException("configuration doesn't exist!");
		conf = TSConfig.getCustom(confFile);
		
		System.out.println("Loaded custom config file " + confFile);
	}
	
	public DataModule(Configuration conf) {
		this.conf = conf;
	}

	public DataModule() {
		conf = TSConfig.getDefault();
	}

	public Configuration getConfiguration() {		
		return conf;
	}
	
	public void setAWSConfig(String accessKeyId, String secretAccessKey, boolean sandbox) {
		conf.addProperty(TSConfig.AWS_ACCESSKEYID, accessKeyId);		
		conf.addProperty(TSConfig.AWS_SECRETACCESSKEY, secretAccessKey);
		conf.addProperty(TSConfig.AWS_SANDBOX, sandbox);
	}

	@Override
	protected void configure() {		
		bind(Configuration.class).toInstance(conf);		
		
		// See providers below for other bindings
		
		// GUI stuff
		bind(TSTabbedPanel.class).in(Scopes.SINGLETON);
	}
	
	@Provides @Singleton 
	RequesterServiceExt getRequesterService() {
		// Create AWS Requester, if any
		RequesterServiceExt req = null;
		try {
			ClientConfig reqConf = TSConfig.getClientConfig(conf);
			req = new RequesterServiceExt(reqConf);
		} catch( RuntimeException e ) {
			e.printStackTrace();			
			System.out.println("Bad configuration for MTurk requester service. MTurk functions will be unavailable.");			
		}
		return req;
	}	

	@Provides @Singleton 
	MysqlConnectionPoolDataSource getMysqlCPDS() {
		// Create a single MySQL connection pool
		return TSConfig.getMysqlCPDS(conf);			
	}	

}
