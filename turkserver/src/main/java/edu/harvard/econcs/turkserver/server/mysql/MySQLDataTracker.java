package edu.harvard.econcs.turkserver.server.mysql;

import edu.harvard.econcs.turkserver.config.TSConfig;
import edu.harvard.econcs.turkserver.schema.*;
import edu.harvard.econcs.turkserver.server.HITWorkerImpl;
import edu.harvard.econcs.turkserver.server.SessionRecord;
import edu.harvard.econcs.turkserver.server.SessionRecord.SessionStatus;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;

import org.apache.commons.configuration.Configuration;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.jolbox.bonecp.BoneCPDataSource;
import com.mysema.query.QueryFlag.Position;
import com.mysema.query.sql.MySQLTemplates;
import com.mysema.query.sql.SQLQuery;
import com.mysema.query.sql.SQLQueryImpl;
import com.mysema.query.sql.SQLTemplates;
import com.mysema.query.sql.dml.SQLDeleteClause;
import com.mysema.query.sql.dml.SQLInsertClause;
import com.mysema.query.sql.dml.SQLUpdateClause;
import com.mysema.query.types.TemplateExpressionImpl;
import com.mysema.query.types.expr.Wildcard;
import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;

/**
 * Connects to a mysql database for persistent users across sessions
 * 
 * schema for turk experiments
 * 
 * HIT ID 30 characters
 * Assignment ID 30 characters
 * Worker ID 14 characters
 * 
 * username 40 characters
 * 
 * @author mao
 *
 */
@Singleton
public class MySQLDataTracker extends ExperimentDataTracker {	
	
	QSets _sets = QSets.sets;
	QExperiment _experiment = QExperiment.experiment;
	QRound _round = QRound.round;
	QSession _session = QSession.session;
	QQual _qual = QQual.qual1;
	QQuiz _quiz = QQuiz.quiz;
	QWorker _worker = QWorker.worker;
	
	private String setID;		
	
	private final BoneCPDataSource pbds;	
	private final SQLTemplates dialect;	

	@Inject
	public MySQLDataTracker(MysqlConnectionPoolDataSource ds) {
		super();		
		/*
		 * Setup BoneCP
		 * TODO fix these settings more flexibly
		 * possible deadlocks observed with getConnection
		 */
		pbds = new BoneCPDataSource();		
		pbds.setDatasourceBean(ds);
		pbds.setMinConnectionsPerPartition(1);
		
//		pbds.setCloseConnectionWatch(true);
//		pbds.setMaxConnectionsPerPartition(5); // Want some deadlocks?
		
		pbds.setMaxConnectionsPerPartition(10);
		
		pbds.setIdleConnectionTestPeriodInMinutes(60);
		pbds.setIdleMaxAgeInMinutes(240);
		pbds.setPartitionCount(1);
						 							
		dialect = new MySQLTemplates();					
	}
	
	@Inject
	public void setSetId(@Named(TSConfig.EXP_SETID) String setID) {
		this.setID = setID;		
		
		System.out.println("Experiment set ID is " + setID);
		
		try( Connection conn = pbds.getConnection() ) {	
			
			// ensure this setId exists
			new SQLInsertClause(conn, dialect, _sets)
			.columns(_sets.name)
			.values(setID)
			.addFlag(Position.START_OVERRIDE, "INSERT IGNORE INTO ")
			.execute();
		} catch (SQLException e) {			
			e.printStackTrace();
		}
	}
	
	public List<Sets> getAllSets() {
		try( Connection conn = pbds.getConnection() ) {				
			// ensure this setId exists
			return new SQLQueryImpl(conn, dialect)
			.from(_sets)			
			.list(_sets);
		} catch (SQLException e) {			
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Experiment getExperiment(String experimentId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			return new SQLQueryImpl(conn, dialect)
			.from(_experiment)
			.where(_experiment.id.eq(experimentId))
			.singleResult(_experiment);
		} catch (SQLException e) {			
			e.printStackTrace();
		} 
		return null;		
	}

	@Override
	public Collection<Experiment> getSetExperiments() {
		try( Connection conn = pbds.getConnection() ) {	
			
			return new SQLQueryImpl(conn, dialect)
			.from(_experiment)
			.where(_experiment.setId.eq(setID))
			.list(_experiment);
		} catch (SQLException e) {			
			e.printStackTrace();
		} 
		return null;
	}

	@Override
	public Multimap<Experiment, Session> getAllExperimentSessions() {
		Multimap<Experiment, Session> result = HashMultimap.create();
		Collection<Experiment> expsInSet = getSetExperiments();
		
		try( Connection conn = pbds.getConnection() ) {			
			for( Experiment e : expsInSet ) {
				List<Session> sessions = new SQLQueryImpl(conn, dialect)
				.from(_session)
				.where(_session.experimentId.eq(e.getId()))
				.list(_session);
				
				for( Session s : sessions ) result.put(e, s);
			}			
		} catch (SQLException e) {			
			e.printStackTrace();
			return null;
		} 
		
		return result;
	}

	@Override
	public List<Round> getExperimentRounds(String experimentId) {
		try( Connection conn = pbds.getConnection() ) {			
			
			return new SQLQueryImpl(conn, dialect)
			.from(_round)
			.where(_round.experimentId.eq(experimentId))
			.list(_round);
										
		} catch (SQLException e) {			
			e.printStackTrace();			
		} 		
		return null;
	}

	@Override
	public Multimap<Experiment, Round> getAllExperimentRounds() {
		Multimap<Experiment, Round> result = HashMultimap.create();
		Collection<Experiment> expsInSet = getSetExperiments();
		
		try( Connection conn = pbds.getConnection() ) {			
			for( Experiment e : expsInSet ) {
				List<Round> rounds = new SQLQueryImpl(conn, dialect)
				.from(_round)
				.where(_round.experimentId.eq(e.getId()))
				.list(_round);
				
				for( Round r : rounds ) result.put(e, r);
			}			
		} catch (SQLException e) {			
			e.printStackTrace();
			return null;
		} 
		
		return result;
	}

	@Override
	public List<Session> getCompletedSessions() {
		try( Connection conn = pbds.getConnection() ) {
			
//			"SELECT hitId, assignmentId, workerId " +				
//			"FROM session " +				
//			"WHERE inactivePercent IS NOT NULL " +
//			"AND setId=? " + // Use this line to filter by some set
//			"AND paid IS NULL";
			
			return new SQLQueryImpl(conn, dialect)
			.from(_session)
			.where(_session.setId.eq(setID),
					_session.inactivePercent.isNotNull())
			.list(_session);
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 				
		return null;
	}

	@Override
	public boolean hitExistsInDB(String hitId) {
		try( Connection conn = pbds.getConnection() ) {	
						
			long count = new SQLQueryImpl(conn, dialect)
			.from(_session)
			.where(_session.hitId.eq(hitId))
			.count();		
			
			return count > 0;
			
			// TODO replicate expired experiment logic elsewhere			
//			if( results != null && results.size() > 0) {
//				Object experimentId = results.get(0)[0];			
//				if( experimentId != null && "EXPIRED".equals(experimentId.toString()) ) 
//					throw new SessionExpiredException();
//				return true;
//			}
//			else return false;
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 
		return false;		
	}

	@Override
	public SessionSummary getSetSessionSummary() {
		try( Connection conn = pbds.getConnection() ) {						
			SQLQuery query;
			
			query = new SQLQueryImpl(conn, dialect);			
			int created = query.from(_session)
					.where(_session.setId.eq(setID))
					.singleResult(Wildcard.countAsInt);
			
			query = new SQLQueryImpl(conn, dialect);
			int assigned = query.from(_session)
					.where(_session.setId.eq(setID),
							_session.workerId.isNotNull())
					.singleResult(Wildcard.countAsInt);
			
			query = new SQLQueryImpl(conn, dialect);
			int completed = query.from(_session)
					.where(_session.setId.eq(setID),
							_session.inactivePercent.isNotNull())
					.singleResult(Wildcard.countAsInt);
			
			query = new SQLQueryImpl(conn, dialect);
			int submitted = query.from(_session)
					.where(_session.setId.eq(setID),
							_session.comment.isNotNull())
					.singleResult(Wildcard.countAsInt);
			
			return new SessionSummary(created, assigned, completed, submitted);
		} catch (SQLException e) {			
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Collection<Quiz> getSetQuizRecords(String workerId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			SQLQuery query = new SQLQueryImpl(conn, dialect);
			
			return query.from(_quiz)
					.where(_quiz.setId.eq(setID), 
							_quiz.workerId.eq(workerId))
					.list(_quiz);
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
		return null;		
	}

	@Override
	public Collection<Session> getSetSessionInfoForWorker(String workerId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			SQLQuery query = new SQLQueryImpl(conn, dialect);
			
			return query.from(_session)
					.where(_session.workerId.eq(workerId),
							_session.setId.eq(setID))
					.list(_session);
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Session getStoredSessionInfo(String hitId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			List<Session> result = new SQLQueryImpl(conn, dialect)
			.from(_session)
			.where(_session.hitId.eq(hitId))
			.list(_session);
				
			// Return the first element if one exists
			return (result == null || result.size() == 0 ? null : result.get(0));
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 
		return null;
	}

	private void ensureWorkerExists(Connection conn, String workerId) {
		/* creates
		 * INSERT IGNORE INTO worker(id) VALUES ("workerId");
		 */
		new SQLInsertClause(conn, dialect, _worker)
		.columns(_worker.id)
		.values(workerId)
		.addFlag(Position.START_OVERRIDE, "INSERT IGNORE INTO ")
		.execute();
	}

	@Override
	public void saveSession(Session record) {
		try( Connection conn = pbds.getConnection() ) {	
			
			// Make sure worker exists
			ensureWorkerExists(conn, record.getWorkerId());						
			
			/* TODO: INSERT ... ON DUPLICATE KEY UPDATE is the safest thing to do here
			 * but not well supported yet. We're okay using saveHITId first.
			 */
			
//			new SQLInsertClause(conn, dialect, _session)			
//			.populate(record)
//			.addFlag(Position.END, TemplateExpressionImpl.create(String.class,
//					" ON DUPLICATE KEY UPDATE ", args))
//			.execute();
			
			new SQLUpdateClause(conn, dialect, _session)
			.where(_session.hitId.eq(record.getHitId()))
			.populate(record)			
			.execute();
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 
	}

	@Override
	public void saveHITId(String hitId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			/*
			 * INSERT INTO session (hitId, setId) VALUES (?, ?)
			 * ON DUPLICATE KEY UPDATE setId = ?
			 */
			
			new SQLInsertClause(conn, dialect, _session)
			.columns(_session.hitId, _session.setId)
			.values(hitId, setID)
			.addFlag(Position.END, TemplateExpressionImpl.create(				
					String.class, " ON DUPLICATE KEY UPDATE {0}", _session.setId.eq(setID) ))
			.execute();
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 
	}

	@Override
	public void saveWorkerAssignment(HITWorkerImpl session,
			String assignmentId, String workerId) {
		try( Connection conn = pbds.getConnection() ) {				
			// Make sure the worker table contains this workerId first, but ignore if already exists
			/*
			 * INSERT IGNORE INTO worker(id) VALUES (?)
			 */
			ensureWorkerExists(conn, workerId);
			
			super.saveWorkerAssignment(session, assignmentId, workerId);			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 		
	}

	@Override
	public void saveQuizResults(String hitId, String workerId, Quiz results) {						
		try( Connection conn = pbds.getConnection() ) {	
			
			ensureWorkerExists(conn, workerId);
			
			results.setSessionId(hitId);
			results.setWorkerId(workerId);
			results.setSetId(setID);
			
			new SQLInsertClause(conn, dialect, _quiz)
			.populate(results)
			.execute();		
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 				
	}

	@Override
	protected void saveExpStartTime(String expId, int size, String inputdata, long startTime) {
		try( Connection conn = pbds.getConnection() ) {	
			
			new SQLInsertClause(conn, dialect, _experiment)
			.columns(_experiment.id, _experiment.setId, _experiment.participants, _experiment.inputdata, _experiment.startTime)
			.values(expId, setID, size, inputdata, new Timestamp(startTime))
			.execute();
			
		} catch (SQLException e) {			
			e.printStackTrace();
		} 		
	}

	@Override
	protected void saveExpRoundStart(String expId, int round, long startTime) {
		try( Connection conn = pbds.getConnection() ) {			
			
			Round r = new Round();
			
			r.setExperimentId(expId);
			r.setStartTime(new Timestamp(startTime));
			r.setRoundnum(round);
			
			new SQLInsertClause(conn, dialect, _round)
			.populate(r)			
			.execute();							
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
	}

	@Override
	protected void saveExpRoundInput(String expId, int round, String inputData) {
		try( Connection conn = pbds.getConnection() ) {			
			
			new SQLUpdateClause(conn, dialect, _round)
			.where(_round.experimentId.eq(expId), _round.roundnum.eq(round))
			.set(_round.inputdata, inputData)			
			.execute();							
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
	}

	@Override
	protected void saveExpRoundEnd(String expId, int round, long endTime, String roundLog) {
		try( Connection conn = pbds.getConnection() ) {			
		
			new SQLUpdateClause(conn, dialect, _round)
			.where(_round.experimentId.eq(expId), _round.roundnum.eq(round))
			.set(_round.endTime, new Timestamp(endTime))
			.set(_round.results, roundLog)
			.execute();							
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
	}

	@Override
	protected void saveExpEndInfo(String expId, long endTime, String logOutput) {			
		try( Connection conn = pbds.getConnection() ) {			
			
			new SQLUpdateClause(conn, dialect, _experiment)
			.where(_experiment.id.eq(expId))
			.set(_experiment.endTime, new Timestamp(endTime))
			.set(_experiment.results, logOutput)
			.execute();							
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
	}

	@Override
	public void clearWorkerForSession(String hitId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			/*
			 * TODO this used to be set to default but not sure how to do with QueryDSL
			 * UPDATE session SET workerId=DEFAULT, username=DEFAULT WHERE hitId=?
			 */
			new SQLUpdateClause(conn, dialect, _session)
			.setNull(_session.workerId)
			.setNull(_session.username)
			.where(_session.hitId.eq(hitId))
			.execute();
			
			logger.info(String.format("HIT %s has workerId cleared", hitId));
		} catch (SQLException e) {			
			e.printStackTrace();
		}	
	}
	
	@Override
	public boolean deleteSession(String hitId) {
		try( Connection conn = pbds.getConnection() ) {	
			
			Session record = new SQLQueryImpl(conn, dialect)
			.from(_session)
			.where(_session.hitId.eq(hitId))
			.singleResult(_session);
			
			if( record == null ) return false;
			
			if( SessionRecord.status(record) == SessionStatus.EXPERIMENT || 
					SessionRecord.status(record) == SessionStatus.COMPLETED ) {
				logger.warn("Refusing to delete session record for {} in experiment or completed", hitId);
				return false;
			}
			
			long deleted = new SQLDeleteClause(conn, dialect, _session)
			.where(_session.hitId.eq(hitId))
			.execute();
			
			return deleted > 0;						
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public List<Session> expireUnusedSessions() {
		try( Connection conn = pbds.getConnection() ) {	
			
			/*
			 * SELECT * FROM session WHERE setId=? AND experimentId IS NULL
			 */
			List<Session> expired = new SQLQueryImpl(conn, dialect)
			.from(_session)
			.where(_session.setId.eq(setID), _session.experimentId.isNull())
			.list(_session);
				
			logger.info("Found " + expired.size() + " unused sessions");
			
			/* 
			 * TODO this used to set to EXPIRED, but now we reuse,
			 * so we can just delete them. Verify that this is okay.  
			 * 
			 * UPDATE session SET experimentId='EXPIRED' WHERE setId=? AND experimentId IS NULL
			 */		
			new SQLDeleteClause(conn, dialect, _session)
			.where(_session.setId.eq(setID), _session.experimentId.isNull())
			.execute();
			
			return expired;
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * Create the TurkServer schema from a configuration.
	 * TODO only works on linux-based machines with mysql client installed.
	 * 
	 * @param conf
	 * @throws Exception
	 */
	public static int createSchema(Configuration conf) throws Exception {
		URL url = MySQLDataTracker.class.getClassLoader().getResource("schema.sql");
		final File f = new File(url.getFile());
		
		if( !f.exists() ) throw new FileNotFoundException("schema.sql was not found");
		
		String host = conf.getString(TSConfig.MYSQL_HOST, null);
		String db = conf.getString(TSConfig.MYSQL_DATABASE, null);
		String user = conf.getString(TSConfig.MYSQL_USER, null);
		String pw = conf.getString(TSConfig.MYSQL_PASSWORD, null);
		
		if( db == null ) throw new Exception ("Need database name!");
		String userStr = user == null ? "" : String.format("-u %s ", user);
		String hostStr = host == null ? "" : String.format("-h %s ", host);
		String pwStr = pw == null ? "" : String.format("-p%s ", pw);
		
		String cmd = String.format("mysql %s %s %s %s", hostStr, userStr, pwStr, db);
		
		System.out.println(cmd + " < schema.sql");		
		
		final Process pr = Runtime.getRuntime().exec(cmd);

		new Thread() {
			public void run() {				 
				try (OutputStream stdin = pr.getOutputStream()) {
					Files.copy(f, stdin);
				} 
				catch (IOException e) { e.printStackTrace(); }							
			}
		}.start();
		
		new Thread() {
			public void run() {				
				try (InputStream stdout = pr.getInputStream() ) {
					ByteStreams.copy(stdout, System.out);
				} 
				catch (IOException e) { e.printStackTrace(); }
			}
		}.start();				

		int exitVal = pr.waitFor();
		if( exitVal == 0 )
			System.out.println("Create db succeeded!");
		else	
			System.out.println("Exited with error code " + exitVal);
		return exitVal;
	}

	public void clearDatabase() {		
		try( Connection conn = pbds.getConnection() ) {	
			
			// clear all tables						
			new SQLDeleteClause(conn, dialect, _round).execute();
			new SQLDeleteClause(conn, dialect, _qual).execute();
			new SQLDeleteClause(conn, dialect, _quiz).execute();
			new SQLDeleteClause(conn, dialect, _session).execute();
			new SQLDeleteClause(conn, dialect, _experiment).execute();
			new SQLDeleteClause(conn, dialect, _worker).execute();
			new SQLDeleteClause(conn, dialect, _sets).execute();
			
			System.out.println("Database emptied.");
			
		} catch (SQLException e) {			
			e.printStackTrace();
		}
	}
	
}
