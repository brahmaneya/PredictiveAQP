package utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class PerformanceAnalysis {
	
	/**
	 * Decide the number of additional tuples to sample per group. Returns map mapping group name to number of 
	 * tuples to sample. positiveSamples and negativeSamples are the number of tuples already sampled. They may be 
	 * used to get current selectivity estimate, which may influence optimal sample size. 
	 * @param sizes
	 * @param positiveSamples
	 * @param negativeSamples
	 * @param scheme
	 * @param num
	 * @return
	 */
	public static Map<String, Integer> generateSampleSizes (Map<String, Double> sizes, Map<String, Integer> positiveSamples, 
			Map<String, Integer> negativeSamples, String scheme, double num) {
		Map<String, Integer> totalSamples = new HashMap<String, Integer>();
		if (scheme.equals("constant")) { // num tuples.
			for (String key : sizes.keySet()) {
				totalSamples.put(key, (int)Math.round(num));
			}
		} else if (scheme.equals("two-third-power")) { // num * t_a^{2/3} tuples. 
			Double n = 0.0;
			for (String key: sizes.keySet()) {
				n += sizes.get(key);
			}
			for (String key : sizes.keySet()) {
				Integer posSamples = 0;
				Integer negSamples = 0;
				if (positiveSamples.containsKey(key)) {
					posSamples = positiveSamples.get(key);
					negSamples = negativeSamples.get(key);
				}
				Integer totalSamplesWanted = (int)Math.round(num * sizes.get(key) / Math.pow(n, 1.0/3));
				totalSamplesWanted -= posSamples + negSamples;
				if (totalSamplesWanted < 0) {
					totalSamplesWanted = 0;
				}
				totalSamples.put(key, totalSamplesWanted);
			}
		}  else if (scheme.equals("two-third-power-selectivity")) { // num * t_a^{2/3} tuples. 
			Double n = 0.0;
			for (String key: sizes.keySet()) {
				n += sizes.get(key);
			}
			for (String key : sizes.keySet()) {
				Integer posSamples = 0;
				Integer negSamples = 0;
				if (positiveSamples.containsKey(key)) {
					posSamples = positiveSamples.get(key);
					negSamples = negativeSamples.get(key);
				}
				Double selectivityEstimate = (posSamples + 1.0) / (posSamples + negSamples + 2.0);
				// Work out below thing properly. Not clear what the effect of selectivity should be.
				Integer totalSamplesWanted = (int)Math.round(num * sizes.get(key) * 2 * 
						Math.sqrt(selectivityEstimate * (1 - selectivityEstimate)) / Math.pow(n, 1.0/3));
				totalSamplesWanted -= posSamples + negSamples;
				if (totalSamplesWanted < 0) {
					totalSamplesWanted = 0;
				}
				totalSamples.put(key, totalSamplesWanted/4); // We only sample 1/4th of what is needed, but do this multiple times.
			}
		} else {
			throw new IllegalArgumentException("scheme parameter not recognized");
		}
		return totalSamples;
	}
	
	/**
	 * Samples from the table, taking totalSamples.get(key) number of tuples for each key, and populates arrays 
	 * positivesSamples and negativeSamples based on how many of the tuples evaluate to positive/negative.
	 * These numbers are decided based on the total number positive and negative tuples per group. Also subtracts
	 * from positive/negative when it finds a positive/negative tuple. (Thus positiveSamples.get(key) + positive.get(key)
	 * is invariant for each key, same for negative.)
	 * @param positive Number of positive tuples per group to begin with. We reduce this as we sample tuples which turn out positive.
	 * @param negative Number of negative tuples per group to begin with. We reduce this as we sample tuples which turn out negative.
	 * @param totalSamples Total number of tuples to be sampled per group.
	 * @param positiveSamples Number of sampled tuples that turned out positive. We populate it. If it is non-empty, then we add to its values.
	 * @param negativeSamples Number of sampled tuples that turned out negative. We populate it. If it is non-empty, then we add to its values.
	 */
	public static void sample (Map<String, Double> sizes, Map<String, Integer> positive, Map<String, Integer> negative,
			Map<String, Integer> totalSamples, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
		Random r = new Random();
		for (String key : totalSamples.keySet()) {
			if (!positiveSamples.containsKey(key)) {
				positiveSamples.put(key, 0);
				negativeSamples.put(key, 0);
			}
			Integer toSample = totalSamples.get(key);
			Integer positives = positive.get(key);
			Integer negatives = negative.get(key);
			while (toSample > 0 && positives + negatives > 0) {
				if (r.nextInt(positives + negatives) < positives) {
					positives--;
				} else {
					negatives--;
				}
				toSample--;
			}
			positiveSamples.put(key, positiveSamples.get(key) + positive.get(key) - positives);
			negativeSamples.put(key, negativeSamples.get(key) + negative.get(key) - negatives);
			positive.put(key, positives);
			negative.put(key, negatives);
			sizes.put(key, positives.doubleValue() + negatives.doubleValue());
		}
	}
	
	/**
	 * Reverses the effect of the sampling by setting positiveSamples and negativeSamples to zero and re-increasing
	 * positive, negative and sizes.
	 */
	public static void unsample (Map<String, Double> sizes, Map<String, Integer> positive, Map<String, Integer> negative,
			Map<String, Integer> totalSamples, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
		for (String key : sizes.keySet()) {
			if (positiveSamples.containsKey(key)) {
				positive.put(key, positive.get(key) + positiveSamples.get(key));
				negative.put(key, negative.get(key) + negativeSamples.get(key));
				sizes.put(key, positive.get(key).doubleValue() + negative.get(key).doubleValue());
				positiveSamples.put(key, 0);
				negativeSamples.put(key, 0);				
			}
		}
	}
	
	/**
	 * Partially reverses the effect of the sampling by setting positiveSamples and negativeSamples to target and re-increasing
	 * positive, negative and sizes.
	 */
	public static void partialUnsample (Map<String, Double> sizes, Map<String, Integer> positive, Map<String, Integer> negative,
			Map<String, Integer> totalSamples, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples,
			Map<String, Integer> targetPositiveSamples, Map<String, Integer> targetNegativeSamples) {
		for (String key : sizes.keySet()) {
			if (positiveSamples.containsKey(key)) {
				positive.put(key, positive.get(key) + positiveSamples.get(key) - targetPositiveSamples.get(key));
				negative.put(key, negative.get(key) + negativeSamples.get(key) - targetNegativeSamples.get(key));
				sizes.put(key, positive.get(key).doubleValue() + negative.get(key).doubleValue());
				positiveSamples.put(key, targetPositiveSamples.get(key));
				negativeSamples.put(key, targetNegativeSamples.get(key));				
			}
		}
	}
	
	/**
	 * Find statistics related to retrieve/evaluate solution. Returns a map mapping strings to doubles.
	 * For examples, maps "recall" to recall, "precision" to precision, and so on.
	 * 
	 * @return
	 */
	public static Map<String, Double> findStats (Map<String, Double> sizes, Map<String, Double> retrieve, Map<String, Double> evaluate,
			Map<String, Integer> positive, Map<String, Integer> negative, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples) {
		Map<String, Double> statsMap = new HashMap<String, Double>();
		Integer retrieves = 0;
		Integer evaluates = 0;
		Integer falsePositives = 0;
		Integer falseNegatives = 0;
		Integer truePositives = 0;
		Integer trueNegatives = 0;
		Double  expectedRetrieves = 0.0;
		Double  expectedEvaluates = 0.0;
		Random r = new Random();
		
		for (String key : sizes.keySet()) {
			expectedRetrieves += sizes.get(key) * retrieve.get(key);
			expectedEvaluates += sizes.get(key) * evaluate.get(key);
			if (positiveSamples.containsKey(key)) {
				expectedRetrieves += positiveSamples.get(key) + negativeSamples.get(key);
				expectedEvaluates += positiveSamples.get(key) + negativeSamples.get(key);
			}
			
			final double retrieveProb = retrieve.get(key);
			final double evaluateProb = evaluate.get(key);
			final double positives = positive.get(key);
			final double negatives = negative.get(key);
			Double rand;
			for (int i = 0; i < positives; i++) {
				rand = r.nextDouble();
				if (rand > retrieveProb) {
					falseNegatives++;
				} else if (rand < evaluateProb) {
					truePositives++;
					evaluates++;
					retrieves++;
				} else {
					truePositives++;
					retrieves++;
				}
			}
			for (int i = 0; i < negatives; i++) {
				rand = r.nextDouble();
				if (rand > retrieveProb) {
					trueNegatives++;
				} else if (rand < evaluateProb) {
					trueNegatives++;
					evaluates++;
					retrieves++;
				} else {
					falsePositives++;
					retrieves++;
				}
			}
			
			if (positiveSamples.containsKey(key)) {
				retrieves += positiveSamples.get(key);
				retrieves += negativeSamples.get(key);
				evaluates += positiveSamples.get(key);
				evaluates += negativeSamples.get(key);
				truePositives += positiveSamples.get(key);
				trueNegatives += negativeSamples.get(key);				
			}
		}

		statsMap.put("expectedRetrieves", expectedRetrieves);
		statsMap.put("expectedEvaluates", expectedEvaluates);
		//statsMap.put("retrieves", retrieves.doubleValue());
		//statsMap.put("evaluates", evaluates.doubleValue());
		statsMap.put("recall", truePositives.doubleValue()/(truePositives + falseNegatives));
		statsMap.put("precision", truePositives.doubleValue()/(truePositives + falsePositives));
		return statsMap;
	}
	
	public static Map<String, Double> precisionRecallThresholdFraction (Map<String, Double> sizes, Map<String, Double> retrieve, Map<String, Double> evaluate,
			Map<String, Integer> positive, Map<String, Integer> negative, Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples, 
			Double alpha, Double beta, Integer iterations) {
		int precisionCount = 0;
		int recallCount = 0;
		Map<String, Double> avgStats = new HashMap<String, Double>();
		for (int i=0; i < iterations; i++) {
			Map<String, Double> stats= findStats (sizes, retrieve, evaluate, positive, negative, positiveSamples, negativeSamples);
			if (stats.get("recall") >= beta) {
				recallCount++;
			}
			if (stats.get("precision") >= alpha) {
				precisionCount++;
			}
		}
		avgStats.put("recall", recallCount/iterations.doubleValue());
		avgStats.put("precision", precisionCount/iterations.doubleValue());
		return avgStats;
	}
	
	/**
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param evaluate			Maps to the evaluation probability of tuple class.
	 * @return					Expected number of tuples evaluated.
	 */
	public static Double evaluateCost (Map<String, Double> sizes, Map<String, Double> evaluate) {
		Double cost = 0.0;
		for (String key : sizes.keySet()) {
			cost += sizes.get(key)*evaluate.get(key);
		}
		return cost;
	}
	
	/**
	 * @param sizes 			Maps tuple class to number of tuples.
	 * @param retrieve 			Maps to the retrieval probability of tuple class.
	 * @return					Expected number of tuples retrieved.
	 */
	public static Double retrieveCost (Map<String, Double> sizes, Map<String, Double> retrieve) {
		Double cost = 0.0;
		for (String key : sizes.keySet()) {
			cost += sizes.get(key)*retrieve.get(key);
		}
		return cost;
	}
}
