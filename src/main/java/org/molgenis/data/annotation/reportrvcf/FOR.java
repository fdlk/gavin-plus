package org.molgenis.data.annotation.reportrvcf;

import org.molgenis.cgd.CGDEntry;
import org.molgenis.data.Entity;
import org.molgenis.data.annotation.entity.impl.gavin.Judgment;
import org.molgenis.data.annotation.makervcf.structs.RelevantVariant;
import org.molgenis.data.annotation.makervcf.structs.VcfEntity;
import org.molgenis.data.vcf.VcfRepository;
import org.molgenis.data.vcf.datastructures.Trio;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * Created by joeri on 6/29/16.
 * <p>
 * False Omission Rate
 * <p>
 * TODO: make gene stream
 */
public class FOR {

    private File originalVcfFile;
    private File rvcfFile;
    private File genesFORoutput;

    /*
    args[0] = working dir (e.g. /Users/joeri/Desktop/GAVIN-APP/1000G_diag_FDR/FOR/)
    args[1] = original VCF (e.g. GAVIN_FOR_benchmark_goldstandard_nodup_gonl.vcf)
    args[2] = RVCF output (e.g. RVCF_GAVIN_FOR_benchmark_goldstandard_nodup_gonl_r1.0.vcf)
    args[3] = FOR summary per gene, output file (e.g. FOR.tsv)
     */
    public static void main(String[] args) throws Exception {
        String dir = args[0];
        FOR falseOM = new FOR(new File(dir+args[1]), new File(dir+args[2]), new File(dir+args[3]));
        falseOM.go();
    }

    public FOR(File originalVcfFile, File rvcfFile, File genesFORoutput) throws Exception {
        this.originalVcfFile = originalVcfFile;
        this.rvcfFile = rvcfFile;
        this.genesFORoutput = genesFORoutput;

    }

    public void go() throws Exception {
        //first, since we don't know the original, read the input VCF file that was processed before
        //and count how many pathogenic variants per gene should have been found
        //assumes that all variants in this list are (likely) pathogenic
        VcfRepository vcf = new VcfRepository(originalVcfFile, "vcf");
        Iterator<Entity> originalVcfIterator = vcf.iterator();

        HashMap<String, String> variantToGene = new HashMap<String, String>(); //e.g. 10_126092389_G_A -> OAT, 10_126097170_C_T -> OAT
        while (originalVcfIterator.hasNext()) {
            VcfEntity record = new VcfEntity(originalVcfIterator.next());

            String gene;
            if (record.getId() != null && record.getId().split(":", -1).length == 2) {
                gene = record.getId().split(":", -1)[0];
            } else {
                if (record.getGenes().size() > 1) {
                    throw new Exception("more than 1 gene (" + record.getGenes().toString() + ") for " + record.toString());
                }
                gene = record.getGenes().toArray()[0].toString();
            }

            variantToGene.put(record.getChr() + "_" + record.getPos() + "_" + record.getRef() + "_" + record.getAlt(), gene);


        }

        System.out.println("pathogenic variants, size BEFORE removing detected variants: " + variantToGene.size());



        HashMap<String, Integer> countPerGeneExpected = new HashMap<String, Integer>();
        for (String variant : variantToGene.keySet()) {
            String gene = variantToGene.get(variant);
            if (countPerGeneExpected.containsKey(gene)) {
                countPerGeneExpected.put(gene, countPerGeneExpected.get(gene) + 1);
            } else {
                countPerGeneExpected.put(gene, 1);
            }
        }


        System.out.println("gold standard patho variant counts per gene: " + countPerGeneExpected.toString());


        VcfRepository rvcf = new VcfRepository(rvcfFile, "vcf");

        Iterator<Entity> rvcfIterator = rvcf.iterator();

        //remove variants seen in in RVCF
        while (rvcfIterator.hasNext()) {
            VcfEntity record = new VcfEntity(rvcfIterator.next());
            String key = record.getChr() + "_" + record.getPos() + "_" + record.getRef() + "_" + record.getAlt();
            variantToGene.remove(key);
        }


        System.out.println("pathogenic variants, size AFTER removing detected variants: " + variantToGene.size());

        HashMap<String, Integer> countPerGeneLeftover = new HashMap<String, Integer>();

        for (String variant : variantToGene.keySet()) {
            String gene = variantToGene.get(variant);

            if (countPerGeneLeftover.containsKey(gene)) {
                countPerGeneLeftover.put(gene, countPerGeneLeftover.get(gene) + 1);
            } else {
                countPerGeneLeftover.put(gene, 1);
            }
        }

        System.out.println("left over variant counts per gene: " + countPerGeneLeftover.toString());


        PrintWriter pw = new PrintWriter(genesFORoutput);
        pw.println("Gene" + "\t" + "Expected" + "\t" + "Observed" + "\t" + "MissedFrac");
        for (String gene : countPerGeneExpected.keySet()) {
            //if no leftovers, subtract 0
            int subtract = 0;
            if (countPerGeneLeftover.containsKey(gene)) {
                //  System.out.println("countPerGeneLeftover.containsKey(gene) " + gene);
                subtract = countPerGeneLeftover.get(gene);
            }
            int exp = countPerGeneExpected.get(gene);
            int obs = (countPerGeneExpected.get(gene) - subtract);
            pw.println(gene + "\t" + exp + "\t" + obs + "\t" + ((1.0 - ((double) obs / (double) exp))));
        }
        pw.flush();
        pw.close();

    }
}
