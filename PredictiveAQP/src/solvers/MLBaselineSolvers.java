package solvers;

import static java.lang.System.out;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import utils.PerformanceAnalysis;
import weka.classifiers.functions.Logistic;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.classifiers.collective.CollectiveClassifier;
import weka.classifiers.collective.meta.SimpleCollective; // Use proper semi-supervised classifier.

import dataextraction.CensusDataExtractor;
import dataextraction.LendingClubDataExtractor;
import dataextraction.MarketingDataExtractor;
import dataextraction.ProsperDataExtractor;

public class MLBaselineSolvers {
	/**
	 * Like logisticRegressionGroups, but uses semi supervised learning. It uses the arff file at IMPROVEDFILELOCATION. The file needs to be created using 
	 * the procedure described near the top of logisticRegressionSolvers. 
	 */
	public static void semiSupervisedLearningGroups (String inputFile, Integer target, Map<String, Double> sizes, Map<String, Double> selectivities, 
			Map<String, Integer> positiveSamples, Map<String, Integer> negativeSamples, Double trainFraction) throws Exception {
		SimpleCollective classifier = new SimpleCollective();
		
		DataSource source = new DataSource(inputFile);
		Instances instances = source.getDataSet(target);
		for (int i = 0; i < instances.numAttributes(); i++) {
			Attribute attr = instances.attribute(i);
			if (attr.numValues() > 50) {
				instances.deleteAttributeAt(i);
				i--;
			}
		}
		instances.randomize(new Random());
		Instances trainInstances = new Instances(instances, (int)(trainFraction*instances.numInstances()));
		Instances testInstances = new Instances(instances, (int)((1-trainFraction)*instances.numInstances()));
		for (int i = 0; i < instances.numInstances(); i++) {
			if (Math.random() < trainFraction) {
				trainInstances.add(instances.instance(i));
			} else {
				testInstances.add(instances.instance(i));
			}
		}
		
		Instance myInstance = testInstances.firstInstance();
		
		//out.println("Training data:\t" + trainInstances.numInstances());
		//out.println("Test data:\t" + testInstances.numInstances());
		
		Long time1 = System.currentTimeMillis();
		classifier.setNumRestarts(1);
		classifier.setNumIterations(10);
		classifier.buildClassifier(trainInstances, testInstances);
		Long time2 = System.currentTimeMillis();
		//out.println("Training Time:\t" + (time2 -time1));
		
		double truePositives = 0.0;
		double falsePositives = 0.0;
		double falseNegatives = 0.0;
		double trueNegatives = 0.0;
		for (Instance instance : testInstances) {
			double[] distribution = classifier.distributionForInstance(instance);	
			if (Math.random() < 0.005) {
				//out.println(distribution[0] + ", " + distribution[1] + "\t" + classification + "\t" + trueClass);
			}
			
			final Double classification = classifier.classifyInstance(instance);
			final Double trueClass = instance.classValue();
			
			double score = Math.abs(trueClass - distribution[0]);
			double prob = distribution[1];
			/*
			if (trueClass.equals(0.0)) {
				truePositives += prob;
				falseNegatives += 1 - prob;
			} else {
				falsePositives += prob;
				trueNegatives += 1 - prob;
			}
			*/
			if (trueClass.equals(0.0)) {
				if (classification.equals(0.0)) {
					truePositives += 1.0;
				} else {
					falseNegatives += 1.0;
				}
			} else {
				if (classification.equals(0.0)) {
					falsePositives += 1.0;
				} else {
					trueNegatives += 1.0;
				}
			}
			
		}
		
		final double evaluates = trainInstances.numInstances();
		final double retrieves = truePositives + falsePositives + evaluates;
		
		for (Instance instance : trainInstances) {
			Double classValue = instance.classValue();
			if (classValue.equals(0.0)) {
				truePositives += 1.0;
			} else {
				trueNegatives += 1.0;
			}
		}
		
		/*
		out.println("TruePositives:\t" + truePositives/testInstances.numInstances());
		out.println("FalsePositives:\t" + falsePositives/testInstances.numInstances());
		out.println("FalseNegatives:\t" + falseNegatives/testInstances.numInstances());
		out.println("TrueNegatives:\t" + trueNegatives/testInstances.numInstances());
		*/
		Double precision = truePositives / (truePositives + falsePositives);
		Double recall = truePositives / (truePositives + falseNegatives);
		out.println(retrieves + "\t" + evaluates + "\t" + precision + "\t" + recall);
		
	}
	
	public static void logisticImputation (String inputFile, Integer target, Map<String, Double> statistics, Double trainFraction) 
			throws Exception {
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
		Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
		LogisticRegressionSolvers.logisticRegressionGroups (inputFile, target, sizes, selectivities, positiveSamples, negativeSamples, 
				50, trainFraction, "interval-length");
		Double truePositives = 0.0;
		Double trueNegatives = 0.0;
		Double falsePositives = 0.0;
		Double falseNegatives = 0.0;
		for (String key : positiveSamples.keySet()) {
			truePositives += positiveSamples.get(key);
			trueNegatives += negativeSamples.get(key);
		}
		
		for (String key : sizes.keySet()) {
			Double prob = Double.parseDouble(key);
			//out.println(key + "\t" + prob + "\t" + sizes.get(key) + "\t" + selectivities.get(key));
			if (sizes.get(key).equals(0.0)) {
				continue;
			}
			final Double trues = sizes.get(key) * selectivities.get(key);
			final Double falses = sizes.get(key) * (1 - selectivities.get(key));
			truePositives += trues * prob;
			trueNegatives += falses * (1 - prob);
			falsePositives += falses * prob;
			falseNegatives += trues * (1 - prob);
		}

		Double precision = truePositives/(truePositives + falsePositives);
		Double recall = truePositives/(truePositives + falseNegatives);
		Double total = truePositives + trueNegatives + falsePositives + falseNegatives;
		Double evaluates = total * trainFraction;
		Double retrieves = evaluates + truePositives + falsePositives;
		out.println(retrieves + "\t" + evaluates + "\t" + precision + "\t" + recall);
	}
	
	public static void main (String[] argv) throws Exception {
		String inputFileLocation = ProsperDataExtractor.ARFFFILELOCATION;
		Integer target = ProsperDataExtractor.TARGET;
		Double trainFraction = 0.25;
		/*Map<String, Double> statistics = new HashMap<String, Double>();
		for (trainFraction = 0.35; trainFraction < 0.70; trainFraction += 0.05) {
			logisticImputation (inputFileLocation, target, statistics, trainFraction);
		}
		if(1!=2) return;*/
		Map<String, Double> sizes = new HashMap<String, Double>();
		Map<String, Double> selectivities = new HashMap<String, Double>();
		Map<String, Integer> positiveSamples = new HashMap<String, Integer>();
		Map<String, Integer> negativeSamples = new HashMap<String, Integer>();
		for (trainFraction = 0.55; trainFraction < 0.7; trainFraction += 0.02) {
			semiSupervisedLearningGroups(inputFileLocation, target, sizes, selectivities, positiveSamples, negativeSamples, trainFraction);
			/*
			System.out.println(sizes.toString());
			System.out.println("Fraction: " + trainFraction);
			System.out.println(sizes.toString());
			System.out.println(selectivities.toString());
			System.out.println(positiveSamples.toString());
			System.out.println(negativeSamples.toString());
			*/
		}
	}
}
