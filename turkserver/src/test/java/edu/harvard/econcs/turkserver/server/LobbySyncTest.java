package edu.harvard.econcs.turkserver.server;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import edu.harvard.econcs.turkserver.api.HITWorkerGroup;
import edu.harvard.econcs.turkserver.cometd.FakeServerSession;
import edu.harvard.econcs.turkserver.config.TSConfig;
import edu.harvard.econcs.turkserver.schema.Session;

public class LobbySyncTest {

	class TestLobbyListener implements LobbyListener {		
		ConcurrentLinkedQueue<HITWorkerGroup> groups = new ConcurrentLinkedQueue<HITWorkerGroup>();		

		@Override
		public void broadcastLobbyMessage(Object data) {}

		@Override
		public void createNewExperiment(HITWorkerGroupImpl group) {
//			System.out.println("Got " + group);
			groups.add(group);
			
			// Simulate taking some time to make an experiment, since this is in the synchronized block
			try { Thread.sleep(Math.round(Math.random() * 100));
			} catch (InterruptedException e) { e.printStackTrace(); }
		}

		@Override
		public int getNumExperimentsRunning() { return 0; }
		@Override
		public int getNumUsersConnected() { return 0; }
	}

	int total = 300;
	int groupsize = 3;
	
	ReadyStateLobby lobby;
	TestLobbyListener listener;
	
	@Before
	public void setUp() throws Exception {
		Configuration conf = new PropertiesConfiguration();
		conf.addProperty(TSConfig.SERVER_DEBUGMODE, true);
	
		lobby = new ReadyStateLobby(new DummyConfigurator(groupsize), conf);		
		lobby.setListener(listener = new TestLobbyListener());					
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test() throws InterruptedException {
		
		Set<Thread> threadz = new HashSet<>();
		
		final Map<String, Object> lobbyReady = ImmutableMap.<String, Object>of("ready", true);
		
		for( int i = 0; i < total; i++ ) {
			final Session record = new Session();
			record.setHitId("HIT " + i);
			record.setAssignmentId("Assignment " + i);
			record.setWorkerId("Worker " + i);
								
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					// Sleep a random amount of time
					try {
						Thread.sleep(Math.round(Math.random() * 100));
					} catch (InterruptedException e) {}
					
					HITWorkerImpl hitw = new HITWorkerImpl(new FakeServerSession(), record);
					lobby.userJoined(hitw);
					
					// Send a possible random lobby status update for SNAFU checking
					if( Math.random() < 0.5 )
						lobby.updateStatus(hitw, lobbyReady);
				}
			});
			
			t.start();
			threadz.add(t);
		}
		
		for( Thread t : threadz ) t.join();
		
		int totalUsers = 0;
		int totalGroups = 0;
		Set<String> ids = new HashSet<String>();
		
		for( HITWorkerGroup group : listener.groups ) {			
//			System.out.println(group);	
			assertEquals(groupsize, group.groupSize());
			
			totalUsers += group.groupSize();
			totalGroups++;
			
			for( String worker : group.getHITIds() )
				ids.add(worker);
		}
		
		assertEquals(this.total, ids.size());
		assertEquals(this.total / groupsize, totalGroups);
		assertEquals(this.total, totalUsers);
	}

}
