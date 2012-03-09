package edu.harvard.econcs.turkserver;

import java.io.Serializable;

public class QuizResults implements Serializable {
	
	private static final long serialVersionUID = -3864495197126855741L;

	public int correct = 0;
	public int total = 0;
	
	public void addCorrectResponse() {
		correct++;
		total++;
	}

	public void addIncorrectResponse() {
		total++;
	}

}
