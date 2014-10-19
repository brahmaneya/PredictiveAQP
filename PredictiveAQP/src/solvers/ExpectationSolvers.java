package solvers;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import utils.PerformanceAnalysis;

public class ExpectationSolvers {

	/**
	 * fullEvaluate is a trivial baseline that retrieves and evaluates all tuples. 
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param selectivities		Maps tuple class to selectivity of tuples in class.
	 * @param alpha				Precision constraint.
	 * @param beta				Recall constraint.
	 * @param rho				Probability constraint for achieving given precision/recall.
	 * @param retrieve			Used to return the retrieval probability of tuple class.
	 * @param evaluate			Used to return the evaluation probability of tuple class.
	 */
	public static void fullEvaluate(Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Map<String, Double> retrieve, Map<String, Double> evaluate) {
		// Set all retrieves and evaluates to 1.
		for (String key : sizes.keySet()) {
			retrieve.put(key, 1.0);
			evaluate.put(key, 1.0);
		}
	}
	
	/**
	 * greedyEvaluate is a baseline that evaluates all tuples it retrieves, and retrieves till it satisfies the 
	 * recall constraint in expectation (not necessarily with probability rho). 
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param selectivities		Maps tuple class to selectivity of tuples in class.
	 * @param alpha				Precision constraint.
	 * @param beta				Recall constraint.
	 * @param rho				Probability constraint for achieving given precision/recall.
	 * @param retrieve			Used to return the retrieval probability of tuple class.
	 * @param evaluate			Used to return the evaluation probability of tuple class.
	 */
	public static void greedyEvaluate(Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Map<String, Double> retrieve, Map<String, Double> evaluate) {
		// Sorting keys in decreasing order of selectivity.
		List<String> keyList = new ArrayList<String>(selectivities.keySet());
		final Map<String, Double> fSelectivities = selectivities;
		Collections.sort(keyList, new Comparator<String>(){
			@Override
			public int compare(String arg0, String arg1) {
				final Double val0 = fSelectivities.get(arg0);
				final Double val1 = fSelectivities.get(arg1);
				if (val0 > val1) {
					return -1;
				} else if (val0.equals(val1)) {
					return 0;
				} else {
					return 1;
				}
			}
		});
		
		// Computing the rhs of the recall constraint.
		Double recallRHS = 0.0;
		for (String key : keyList) {
			recallRHS += beta * sizes.get(key) * selectivities.get(key);
		}
		
		// Greedily assign retrieves and evaluates. till recall constraint is satisfied in expectation.
		for (String key : keyList) {
			final Double selectivity = selectivities.get(key);
			final Double size = sizes.get(key);
			if (recallRHS <= 0.0) {
				retrieve.put(key, 0.0);
				evaluate.put(key, 0.0);
			} else if (size*selectivity >= recallRHS) {
				retrieve.put(key, recallRHS/(size*selectivity));
				evaluate.put(key, recallRHS/(size*selectivity));
				recallRHS = 0.0;
			} else {
				retrieve.put(key, 1.0);
				evaluate.put(key, 1.0);
				recallRHS -= size*selectivity;
			}
		}
	}
	
	/**
	 * biGreedyEvaluate is our method, that retrieves tuples in decreasing selectivity order, till it satisfies
	 * the recall constraint, and then evaluates in reverse order till it satisfies the precision constraint. 
	 * Both constraints are satisifed in expectation, not with probability rho.
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param selectivities		Maps tuple class to selectivity of tuples in class.
	 * @param alpha				Precision constraint.
	 * @param beta				Recall constraint.
	 * @param rho				Probability constraint for achieving given precision/recall.
	 * @param retrieve			Used to return the retrieval probability of tuple class.
	 * @param evaluate			Used to return the evaluation probability of tuple class.
	 */
	public static void biGreedyEvaluate(Map<String, Double> sizes, Map<String, Double> selectivities, Double alpha, 
			Double beta, Double rho, Map<String, Double> retrieve, Map<String, Double> evaluate) {
		// Sorting keys in decreasing order of selectivity.
		List<String> keyList = new ArrayList<String>(selectivities.keySet());
		final Map<String, Double> fSelectivities = selectivities;
		Collections.sort(keyList, new Comparator<String>(){
			@Override
			public int compare(String arg0, String arg1) {
				final Double val0 = fSelectivities.get(arg0);
				final Double val1 = fSelectivities.get(arg1);
				if (val0 > val1) {
					return -1;
				} else if (val0.equals(val1)) {
					return 0;
				} else {
					return 1;
				}
			}
		});
	
		// Computing the rhs of the recall constraint.
		Double recallRHS = 0.0;
		for (String key : keyList) {
			recallRHS += beta * sizes.get(key) * selectivities.get(key);
		}
		
		Double precisionRHS = 0.0;
		// Greedily assigning retrieves, while evaluating precision constraint rhs.
		for (String key : keyList) {
			final Double selectivity = selectivities.get(key);
			final Double size = sizes.get(key);
			if (recallRHS <= 0.0) {
				final Double r = 0.0;
				retrieve.put(key, r);
			} else if (size*selectivity >= recallRHS) {
				final Double r = recallRHS/(size*selectivity);
				retrieve.put(key, r);
				precisionRHS -= r*size*(selectivity - alpha);
				recallRHS = 0.0;
			} else {
				final Double r = 1.0;
				retrieve.put(key, r);
				precisionRHS -= r*size*(selectivity - alpha);
				recallRHS -= size*selectivity;
			}
		}
		
		// Greedily assigning evaluates, in increasing selectivity order.
		Collections.reverse(keyList);
		for (String key : keyList) {
			final Double selectivity = selectivities.get(key);
			final Double size = sizes.get(key);
			final Double r = retrieve.get(key);
			if (r.equals(0.0)) {
				final Double e = 0.0;
				evaluate.put(key, e);
			} else if (precisionRHS <= 0) {
				final Double e = 0.0;
				evaluate.put(key, e);
			} else if (precisionRHS <= r*alpha*(1-selectivity)*size) {
				final Double e = precisionRHS/(alpha*(1-selectivity)*size);
				evaluate.put(key, e);
				precisionRHS = 0.0;
			} else {
				final Double e = r;
				evaluate.put(key, e);
				precisionRHS -= e*size*alpha*(1-selectivity);
			}
		}
	}
	
	public static void main(String[] argv) {
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		Double alpha; 
		Double beta; 
		Double rho; 
		Map<String, Double> retrieve = new HashMap<String, Double>();
		Map<String, Double> evaluate = new HashMap<String, Double>();
		Double numRetrieved;
		Double numEvaluated;
		
		// Initialize constraints.
		alpha = 0.8;
		beta = 0.8;
		rho = 0.8;
		
		// Initialize table info.
		sizes.put("pos", 560.0);
		sizes.put("neg", 9640.0);
		selectivities.put("pos", 0.87);
		selectivities.put("neg", 0.013);
		
		biGreedyEvaluate(sizes, selectivities, alpha, beta, rho, retrieve, evaluate);

		numRetrieved = PerformanceAnalysis.retrieveCost(sizes, retrieve);
		numEvaluated = PerformanceAnalysis.evaluateCost(sizes, evaluate);
		
		System.out.println(retrieve.toString());
		System.out.println(evaluate.toString());
		System.out.println(numRetrieved.toString());
		System.out.println(numEvaluated.toString());
	}
}
