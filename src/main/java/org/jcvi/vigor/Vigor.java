package org.jcvi.vigor;

import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jcvi.jillion.core.datastore.DataStoreException;
import org.jcvi.jillion.core.datastore.DataStoreProviderHint;
import org.jcvi.jillion.fasta.nt.NucleotideFastaDataStore;
import org.jcvi.jillion.fasta.nt.NucleotideFastaFileDataStoreBuilder;
import org.jcvi.jillion.fasta.nt.NucleotideFastaRecord;
import org.jcvi.vigor.component.Alignment;
import org.jcvi.vigor.component.Model;
import org.jcvi.vigor.component.VirusGenome;
import org.jcvi.vigor.forms.VigorForm;
import org.jcvi.vigor.service.*;
import org.jcvi.vigor.utils.GenerateVigorOutput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class Vigor {

    private static Logger LOGGER = LogManager.getLogger(Vigor.class);

    @Autowired
    private VigorInitializationService initializationService;

    @Autowired
	private AlignmentGenerationService alignmentGenerationService;

    @Autowired
    ModelGenerationService modelGenerationService;

    @Autowired
    private VigorInputValidationService inputValidationService;

    @Autowired
	private GeneModelGenerationService geneModelGenerationService;

	@Autowired
    private GenerateVigorOutput generateVigorOutput;

	public void run(String ... args) {

        Namespace parsedArgs = parseArgs(args);
        VigorForm vigorForm = getVigorForm(parsedArgs);
        Map<String,String> vigorParameters = vigorForm.getVigorParametersList();
        LOGGER.debug( () ->  vigorParameters.entrySet()
                                            .stream()
                                            .sorted(Map.Entry.comparingByKey())
                                            .map( e -> String.format("%s:%s", e.getKey(), e.getValue()))
                                            .collect(Collectors.joining("\n")) );
        String inputFileName = parsedArgs.getString("input_fasta");
        File inputFile = new File(inputFileName);
        // TODO check file exists and is readable
        // TODO check output directory and permissions
        try (NucleotideFastaDataStore dataStore = new NucleotideFastaFileDataStoreBuilder(inputFile)
                .hint(DataStoreProviderHint.RANDOM_ACCESS_OPTIMIZE_SPEED)
                .build();) {
            Stream<NucleotideFastaRecord> records = dataStore.records();
            Iterator<NucleotideFastaRecord> i = records.iterator();
            while (i.hasNext()) {
                NucleotideFastaRecord record = i.next();
                VirusGenome virusGenome = new VirusGenome(record.getSequence(), record.getComment(), record.getId(),
                        "1".equals(vigorParameters.get("complete_gene")),
                        "1".equals(vigorParameters.get("circular_gene")));

                // Call referenceDBGenerationService methods to generate alignmentEvidence.
                List<Alignment> alignments = generateAlignments(virusGenome, vigorForm);
                List<Model> candidateModels = generateModels(alignments, vigorForm);
                List<Model> geneModels = generateGeneModels(candidateModels, vigorForm);
                // TODO checkout output earlier.
                generateOutput(candidateModels, vigorForm.getVigorParametersList().get("output"));
            }

        } catch (DataStoreException e) {
            LOGGER.error(String.format("problem reading input file %s", inputFileName)
                    , e);
            System.exit(1);
        } catch (IOException e) {
            LOGGER.error("file problem", e);
            System.exit(1);
        }


    }

    public Namespace parseArgs(String[] args) {
        return inputValidationService.processInput(args);
    }

    public VigorForm getVigorForm(Namespace args) {
        return initializationService.initializeVigor(args);
    }

    public List<Alignment> generateAlignments(VirusGenome genome, VigorForm form) {
        return alignmentGenerationService.GenerateAlignment(genome, form);
    }

    public List<Model> generateModels(List<Alignment> alignments, VigorForm form) {
        return modelGenerationService.generateModels(alignments, form);
    }

    public List<Model> generateGeneModels(List<Model> models, VigorForm form) {
        return geneModelGenerationService.generateGeneModel(models, form);
    }

    public void generateOutput(List<Model> models, String outputDirectory) {
        generateVigorOutput.generateOutputFiles(outputDirectory, models);
    }


}

