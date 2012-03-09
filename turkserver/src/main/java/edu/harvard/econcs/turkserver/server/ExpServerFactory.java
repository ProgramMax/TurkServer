package edu.harvard.econcs.turkserver.server;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Good factories should cache an experiment to minimize server lag,
 * using the run() method
 * 
 * @author mao
 *
 * @param <T>
 */
public abstract class ExpServerFactory<T extends ExperimentServer<T>> implements Runnable {
	
	/**
	 * Gets a new experiment.   
	 * 
	 * @param host
	 * @param clients
	 * @return
	 * @throws ExperimentFactoryException
	 */
	public abstract T getNewExperiment(HostServer<T> host, ConcurrentHashMap<BigInteger, Boolean> clients)
	throws ExperimentFactoryException;
	
	public abstract int getExperimentSize();

	/**
	 * Default run method, which does nothing.
	 */
	public void run() {
		
	}
	
	public static class ExperimentFactoryException extends Exception {
		private static final long serialVersionUID = -5765011132751659410L;		
	}

}
