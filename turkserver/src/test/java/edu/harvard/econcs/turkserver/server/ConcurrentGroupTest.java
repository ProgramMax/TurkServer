package edu.harvard.econcs.turkserver.server;

import static org.junit.Assert.*;

import java.util.LinkedList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

import edu.harvard.econcs.turkserver.client.LobbyClient;
import edu.harvard.econcs.turkserver.client.TestClient;
import edu.harvard.econcs.turkserver.config.DataModule;
import edu.harvard.econcs.turkserver.config.DatabaseType;
import edu.harvard.econcs.turkserver.config.ExperimentType;
import edu.harvard.econcs.turkserver.config.HITCreation;
import edu.harvard.econcs.turkserver.config.LoggingType;
import edu.harvard.econcs.turkserver.config.TSConfig;
import edu.harvard.econcs.turkserver.config.TestServerModule;

public class ConcurrentGroupTest {
	
	static int clients = 50;
	static int groupSize = 5;	
	
	static int rounds = 5;
	static int delay = 1000;					
	
	SessionServer ss;
	
	@Before
	public void setUp() throws Exception {
		TestUtils.waitForPort(9876);
		TestUtils.waitForPort(9877);
	}

	@After
	public void tearDown() throws Exception {
		if( ss != null ) {
			ss.shutdown();
			ss.join();
		}
	}
	
	class GroupModule extends TestServerModule {
		@Override
		public void configure() {
			super.configure();
			
			bindExperimentClass(TestExperiment.class);				
			bindConfigurator(new TestConfigurator(groupSize, rounds));
			bindString(TSConfig.EXP_SETID, "test");
		}
	}

	@Test(timeout=12000)
	public void test() throws Exception {
		DataModule dataModule = new DataModule();
		dataModule.setHITLimit(clients);
		
		ss = TurkServer.testExperiment(
				dataModule,
				DatabaseType.TEMP_DATABASE,
				ExperimentType.GROUP_EXPERIMENTS,
				HITCreation.NO_HITS,
				LoggingType.SCREEN_LOGGING,
				new GroupModule()
				);
		
		// Give server enough time to initialize
		Thread.sleep(500);
		
		ClientGenerator cg = new ClientGenerator("http://localhost:9876/cometd/");
				
		LinkedList<TestClient> ll = Lists.newLinkedList();
		
		for( int i = 0; i < clients; i++) {
			LobbyClient<TestClient> lc = cg.getClient(TestClient.class);			
			
			TestClient cl = lc.getClientBean();
			cl.setMessage(String.valueOf(i), delay);
			ll.add(cl);
		}		
		
		/*
		 * TODO: 2 earliest experiments are not completing when unit test is run in a group! 
		 */
		
		// Wait for server to shut down
		ss.join();
		
		// Verify that every client finished correctly
		for( TestClient cl : ll )
			assertTrue(cl.finished);
		
		// Verify that every experiment is finished
		assertEquals(0, ss.experiments.manager.beans.size() );
		assertEquals(0, ss.experiments.currentExps.size());
		
	}

}
