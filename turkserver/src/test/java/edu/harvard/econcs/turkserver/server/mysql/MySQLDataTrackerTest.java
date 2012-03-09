package edu.harvard.econcs.turkserver.server.mysql;

import static org.junit.Assert.*;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import edu.harvard.econcs.turkserver.ExpServerException;
import edu.harvard.econcs.turkserver.server.SessionRecord;

import org.apache.commons.configuration.ConfigurationException;
import org.junit.Before;
import org.junit.Test;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

public class MySQLDataTrackerTest {
	
	private MySQLDataTracker dt;
		
	@Before
	public void init() throws ConfigurationException {		
		
		MysqlConnectionPoolDataSource ds = new MysqlConnectionPoolDataSource();
		
		ds.setDatabaseName("turking");
		ds.setUser("turker");
		ds.setPassword("P@$$w0rd");
		
		// To avoid unexpected lost data
		ds.setStrictUpdates(false);	
		
		dt = new MySQLDataTracker(ds, "test", null, 2, 2);
		dt.createSchema(true);
	}
	
	/**
	 * Simple test of session storing capabilities
	 *  
	 * @throws SQLException
	 * @throws ExpServerException
	 * @throws UnknownHostException
	 */
	@Test
	public void testSession() throws SQLException, ExpServerException, UnknownHostException {
				
		String hitID = "HIT12340931";
		String assignmentId = "AsstIJFINGPEWRBNAE";		
		String workerId = "WorkerABJAER";
		String username = "randomuser\" WHERE";
		
		BigInteger sessionID = dt.getNewSessionID();
		// Test for uniqueness (code copied from tracker)		
		assertTrue(dt.sessionExistsInDB(sessionID));		
		// Test that session is not completed
		assertFalse(dt.sessionCompletedInDB(sessionID));
		
		dt.saveHITIdForSession(sessionID, hitID);
		
		// Add shit to database
		dt.registerAssignment(sessionID, assignmentId, workerId);
		assertFalse(dt.sessionCompletedInDB(sessionID));
		
		// Check that sessionIDs stored properly 
		List<SessionRecord> srs = dt.getSetSessionInfoForWorker(workerId);
		assertTrue(srs.size() == 1);
		assertEquals(srs.iterator().next().getId(), sessionID);
		
		// Check that assignmentID is stored correctly
		assertEquals(dt.getStoredSessionInfo(sessionID).getAssignmentId(), assignmentId);
		
		dt.lobbyLogin(sessionID, username);
		dt.saveIPForSession(sessionID, InetAddress.getLocalHost(), new Date());
		// Check that username cached correctly
		assertEquals(dt.getUsername(sessionID), username);
		assertFalse(dt.sessionCompletedInDB(sessionID));
		
		// Test inactivePercent completion detection
		dt.saveSessionCompleteInfo(sessionID, 0.02);		
		assertTrue(dt.sessionCompletedInDB(sessionID));
	}
}
