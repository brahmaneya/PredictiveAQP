package dataextraction;

import static java.lang.System.out;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Sampling {
	public static List<String> getSamples (Double sampleProb, String fileLocation) throws IOException {
		List<String> samples = new ArrayList<String>();
		BufferedReader br = new BufferedReader(new FileReader(fileLocation));
		String s;
		s = br.readLine();
		while ((s = br.readLine()) != null) {
			if (Math.random() > sampleProb) {
				continue;
			}
			samples.add(s);
		}
		br.close();
		return samples;
	}
}
