package org.jcvi.vigor.RegressionTest;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jcvi.jillion.core.Range;
import org.jcvi.vigor.Application;
import org.jcvi.vigor.testing.category.Regression;
import org.jcvi.vigor.testing.category.Slow;
import org.jcvi.vigor.component.Exon;
import org.jcvi.vigor.component.Model;
import org.jcvi.vigor.exception.VigorException;
import org.jcvi.vigor.service.VigorInitializationService;
import org.jcvi.vigor.utils.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;

import static org.junit.Assert.fail;

@Category( { Slow.class, Regression.class })
@RunWith(Parameterized.class)
@ContextConfiguration(classes = Application.class)
public class ValidateVigor4ModelsTest {

    private final static Logger LOGGER = LogManager.getLogger(ValidateVigor4ModelsTest.class);
    private String referenceOutputTBL;
    private String inputFasta;
    private String referenceDatabaseName;
    private String referenceType;
    @Autowired
    GenerateVigor4GeneModels generateVigor4GeneModels;
    @Autowired
    private VigorInitializationService initializationService;
    @ClassRule
    public static final SpringClassRule springClassRule = new SpringClassRule();
    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    public ValidateVigor4ModelsTest ( String referenceOutputTBL, String inputFasta, String referenceDatabaseName, String referenceType ) {

        this.referenceOutputTBL = getResource(referenceOutputTBL).orElseThrow(
                () -> new IllegalArgumentException(String.format("%s not found", referenceOutputTBL)));
        this.inputFasta = getResource(inputFasta).orElseThrow(
                () -> new IllegalArgumentException(String.format("%s not found", inputFasta)));
        this.referenceDatabaseName = referenceDatabaseName;
        this.referenceType = referenceType;
    }

    private Optional<String> getResource ( String resource ) {

        URL url = ValidateVigor4ModelsTest.class.getClassLoader().getResource(resource);
        if (url == null) {
            return Optional.empty();
        }
        return Optional.of(Paths.get(url.getFile()).toAbsolutePath().toString());
    }

    @Parameterized.Parameters(name = "ValidateVigor4ModelsTest[#{index} {0}]")
    public static Collection<Object[]> getTestData () {

        List<Object[]> testData = new ArrayList<>();
        testData.add(
                new Object[] {
                        "vigor3Output/flua/flua.tbl",
                        "vigor3Output/flua/flua.fasta",
                        "flua_db", "Vigor3"
                });
        testData.add(
                new Object[] {
                        "vigor3Output/veev/veev.tbl",
                        "vigor3Output/veev/veev.fasta",
                        "veev_db", true,
                });
        testData.add(
                new Object[] {
                        "vigor3Output/rsv/rsv.tbl",
                        "vigor3Output/rsv/rsv.fasta",
                        "rsv_db", true,
                });
        testData.add(
                new Object[] {
                        "vigor4ReferenceOutput/flua/flua.tbl",
                        "vigor4ReferenceOutput/flua/flua.fasta",
                        "flua_db", "Vigor4"
                });
        testData.add(
                new Object[] {
                        "flanOutput/flua/flua.tbl",
                        "flanOutput/flua/flua.fasta",
                        "flua_db", "Flan"
                });
        return testData;
    }

    public Map<String, List<Model>> getVigor4Models ( VigorConfiguration config, String inputFasta ) throws VigorException, IOException {

        checkConfig(config);
        String referenceDBPath = config.get(ConfigurationParameters.ReferenceDatabasePath);
        String referenceDB = Paths.get(referenceDBPath, referenceDatabaseName).toString();
        VigorUtils.checkFilePath("reference database", referenceDB,
                VigorUtils.FileCheck.EXISTS,
                VigorUtils.FileCheck.READ,
                VigorUtils.FileCheck.FILE);
        return generateVigor4GeneModels.generateModels(inputFasta, referenceDB, config);
    }

    public Map<String, List<Model>> getReferenceModels () throws IOException {

        return new GenerateReferenceModels().generateModels(referenceOutputTBL, inputFasta);
    }

    @Test
    public void validate () throws IOException, VigorException {

        VigorConfiguration config = getConfiguration();
        Map<String, List<String>> errors = compareWithReferenceModels(getVigor4Models(config, inputFasta), getReferenceModels());
        String errorReport = String.format("differencesReport_%sRef.txt", referenceType);
        boolean hasErrors = errors.entrySet()
                .stream()
                .filter(e -> !( e.getValue() == null || e.getValue().isEmpty() ))
                .findAny().isPresent();
        if (hasErrors) {
            StringBuilder sb = new StringBuilder();
            for (String genome : errors.keySet()) {
                if (errors.get(genome).isEmpty()) {
                    continue;
                }
                sb.append("\n**********************************************************************\n");
                sb.append("For Genome: ");
                sb.append(genome);
                sb.append("\n\n");
                sb.append(String.join("\n", errors.get(genome)));
                sb.append("\n\n*********************************************************************\n\n");
            }
            if ("true".equals(System.getProperty("vigor.regression_test.write_report")) ||
                    "true".equals(System.getenv("VIGOR_REGRESSION_TEST_WRITE_REPORT"))) {
                Path reportFile = Paths.get(config.get(ConfigurationParameters.OutputDirectory), errorReport);
                boolean overwrite = "true".equals(config.get(ConfigurationParameters.OverwriteOutputFiles));
                List<OpenOption> openOptionsList = new ArrayList<>();
                if (overwrite) {
                    openOptionsList.add(StandardOpenOption.CREATE);
                    openOptionsList.add(StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    openOptionsList.add(StandardOpenOption.CREATE_NEW);
                }
                try (BufferedWriter writer = Files.newBufferedWriter(reportFile,
                        Charset.forName("UTF-8"),
                        openOptionsList.toArray(new OpenOption[] {}))) {
                    writer.write(sb.toString());
                    writer.flush();
                }
            }
            fail(sb.toString());
        }
    }

    private VigorConfiguration getConfiguration () throws IOException, VigorException {

        VigorConfiguration config = initializationService.mergeConfigurations(initializationService.getDefaultConfigurations());
        String outputPrefix = new File(referenceDatabaseName).getName().replace("_db", "");
        config.putString(ConfigurationParameters.OutputPrefix, outputPrefix);
        String outDir = config.get(ConfigurationParameters.OutputDirectory);
        String tmpDir = config.get(ConfigurationParameters.TemporaryDirectory);
        if (outDir == null) {
            Path outDirPath;
            if (tmpDir != null) {
                outDirPath = Files.createTempDirectory(Paths.get(tmpDir), "vigor4_test");
            } else {
                outDirPath = Files.createTempDirectory("vigor4_test");
            }
            outDir = outDirPath.toString();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> VigorUtils.deleteDirectory(outDirPath)));
        }
        String virusSpecificPath = Paths.get(outDir, outputPrefix).toString();
        File virusSpecificDir = new File(virusSpecificPath);
        if (!( virusSpecificDir.exists() || virusSpecificDir.mkdir() )) {
            throw new VigorException(String.format("virus specific output directory %s doesn't exist and could not be created", virusSpecificPath));
        }
        config.putString(ConfigurationParameters.OutputDirectory, virusSpecificDir.getAbsolutePath());
        config.putString(ConfigurationParameters.Verbose, "false");
        if (config.get(ConfigurationParameters.OverwriteOutputFiles) == null) {
            config.putString(ConfigurationParameters.OverwriteOutputFiles, "true");
        }
        // TODO allow user to set this
        if (tmpDir == null) {
            final Path tempDir = Files.createTempDirectory(Paths.get(outDir), "vigor-tmp");
            // delete on shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> VigorUtils.deleteDirectory(tempDir)));
            config.putString(ConfigurationParameters.TemporaryDirectory, tempDir.toString());
        }
        checkConfig(config);
        return config;
    }

    private void checkConfig ( VigorConfiguration config ) throws VigorException {

        VigorUtils.checkFilePath("reference database path", config.get(ConfigurationParameters.ReferenceDatabasePath),
                VigorUtils.FileCheck.EXISTS, VigorUtils.FileCheck.READ);
        VigorUtils.checkFilePath("temporary directory path", config.get(ConfigurationParameters.TemporaryDirectory),
                VigorUtils.FileCheck.EXISTS, VigorUtils.FileCheck.READ, VigorUtils.FileCheck.WRITE);
        // must be set, (but will be created if it doesn't exist?)
        VigorUtils.checkFilePath("output directory", config.get(ConfigurationParameters.OutputDirectory));
    }

    public Map<String, List<String>> compareWithReferenceModels ( Map<String, List<Model>> allVigor4Models, Map<String, List<Model>> allReferenceModels ) {

        Map<String, List<String>> allErrors = new HashMap<>();
        for (String genome : allReferenceModels.keySet()) {
            List<Model> vigor4Models = allVigor4Models.getOrDefault(genome, Collections.EMPTY_LIST);
            List<String> errors = allErrors.computeIfAbsent(genome, k -> new ArrayList<>());
            if (vigor4Models.isEmpty()) {
                errors.add(String.format("No vigor4 models found for genome %s", genome));
                continue;
            }
            List<Model> refModels = allReferenceModels.get(genome);
            for (Model refModel : refModels) {
                boolean errorFound = false;
                String refGeneID = refModel.getGeneSymbol();
                String refGenomeID = refModel.getAlignment().getVirusGenome().getId();
                Optional<Model> vigor4Model = vigor4Models.stream().filter(m -> refGeneID.equals(m.getGeneSymbol())).findFirst();
                if (!vigor4Model.isPresent()) {
                    errors.add(String.format(referenceType + " reference models & latest Vigor4 models do not match for VirusGenome Sequence %s. Expected gene symbol %s", refGenomeID, refGeneID));
                    errorFound = true;
                } else {
                    List<String> outErrors = compareModels(refModel, vigor4Model.get());
                    if (outErrors.size() > 0) {
                        errorFound = true;
                    }
                    errors.addAll(outErrors);
                }
                if (errorFound) {
                    errors.add(String.format("\nReferenceModel :%s \n\nVigor4Model :%s", refModel.toString(), vigor4Model.get().toString()));
                }
            }
            LOGGER.debug("{} errors for genome {}", errors.size(), genome);
        }
        return allErrors;
    }

    List<String> compareModels ( Model refModel, Model model ) {

        List<String> errors = new ArrayList<>();
        List<Exon> foundExons = model.getExons();
        List<Exon> refExons = refModel.getExons();
        String refGenomeID = refModel.getAlignment().getVirusGenome().getId();
        String expectedGeneSymbol = refModel.getGeneSymbol();
        if (model.isPartial3p() != refModel.isPartial3p()) {
            errors.add(String.format("Partial 3' gene feature mismatch found for gene %s of VirusGenome Sequence %s", expectedGeneSymbol, refGenomeID));
        }
        if (refModel.isPartial5p() != model.isPartial5p()) {
            errors.add(String.format("Partial 5' gene feature mismatch found for gene %s of VirusGenome Sequence %s", expectedGeneSymbol, refGenomeID));
        }
        if (refModel.getRibosomalSlippageRange() == null ^ model.getRibosomalSlippageRange() == null) {
            errors.add(String.format("Ribosomal Slippage mismatch found for gene %s of VirusGenome Sequence %s", expectedGeneSymbol, refGenomeID));
        }
        if (refModel.getReplaceStopCodonRange() == null ^ model.getReplaceStopCodonRange() == null) {
            errors.add(String.format("Stop Codon Readthrough feature mismatch found for gene %s of VirusGenome Sequence %s", expectedGeneSymbol, refGenomeID));
        }
        if (refModel.isPseudogene() != model.isPseudogene()) {
            errors.add(String.format("Pseudogene feature mismatch found for gene %s of VirusGenome Sequence %s", expectedGeneSymbol, refGenomeID));
        }
        if (foundExons.size() != refExons.size()) {
            errors.add(String.format("Exon count differs for models with gene symbol %s of VirusGenome Sequence %s", expectedGeneSymbol, refGenomeID));
        } else {
            for (int i = 0; i < refExons.size(); i++) {
                Range vigor3ExonRange = refExons.get(i).getRange();
                Range vigor4ExonRange = foundExons.get(i).getRange();
                if (!vigor4ExonRange.equals(vigor3ExonRange)) {
                    errors.add(String.format("Exon range differs for models with gene symbol %s of VirusGenome Sequence %s. Expected %s , found %s ",
                            expectedGeneSymbol, refGenomeID, vigor3ExonRange.toString(Range.CoordinateSystem.RESIDUE_BASED),
                            vigor4ExonRange.toString(Range.CoordinateSystem.RESIDUE_BASED)
                    ));
                }
            }
        }
        return errors;
    }
}
