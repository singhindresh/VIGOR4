package org.jcvi.vigor.service;

import java.util.Map;

import org.jcvi.vigor.component.Model;
import org.jcvi.vigor.utils.ConfigurationParameters;
import org.jcvi.vigor.utils.VigorConfiguration;
import org.springframework.stereotype.Service;

@Service
public class EvaluateScores implements EvaluateModel {


    @Override
    public Model evaluate ( Model model, VigorConfiguration defaultConfiguration ) {

        Map<String, Double> scores = model.getScores();
        VigorConfiguration configuration = model.getAlignment().getViralProtein().getConfiguration();
        double alignmentScoreFactor = configuration.getOrDefault(ConfigurationParameters.ScoreFactorAlignment, 1d);

        double startScoreFactor = configuration.getOrDefault(ConfigurationParameters.ScoreFactorStart, 1d);
        double splicingScoreFactor = configuration.getOrDefault(ConfigurationParameters.ScoreFactorSplicing, 1d);
        double stopScoreFactor = configuration.getOrDefault(ConfigurationParameters.ScoreFactorStop, 1d);
        double leakyStopScoreFactor = configuration.getOrDefault(ConfigurationParameters.ScoreFactorLeakyStop, 1d);

        double alignmentScore = 0;
        double startCodonScore = 0;
        double splicingScore = 0;
        double leakyStopScore = 0;
        double stopScore = 0;
        double totalScore;
        if (scores.get("alignmentScore") != null) {
            alignmentScore = scores.get("alignmentScore") * alignmentScoreFactor;
        }
        if (scores.get("startCodonScore") != null) {
            startCodonScore = scores.get("startCodonScore") * startScoreFactor;
        }
        if (scores.get("leakyStopScore") != null) {
            leakyStopScore = scores.get("leakyStopScore") * leakyStopScoreFactor;
        }
        if (scores.get("spliceScore") != null) {
            splicingScore = scores.get("spliceScore") * splicingScoreFactor;
        }
        if (scores.get("stopCodonScore") != null) {
            stopScore = scores.get("stopCodonScore") * stopScoreFactor;
        }
        scores.put("alignmentScore", alignmentScore);
        scores.put("startCodonScore", startCodonScore);
        scores.put("leakyStopScore", leakyStopScore);
        scores.put("spliceScore", splicingScore);
        scores.put("stopCodonScore", stopScore);
        totalScore = alignmentScore + startCodonScore + leakyStopScore + splicingScore + stopScore;
        scores.put("totalScore", totalScore);
        return model;
    }
}
