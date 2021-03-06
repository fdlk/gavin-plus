package org.molgenis.data.annotation.makervcf.genestream.impl;

import org.apache.commons.lang.StringUtils;
import org.molgenis.data.annotation.makervcf.genestream.core.ConvertToGeneStream;
import org.molgenis.data.annotation.makervcf.genestream.core.GeneStream;
import org.molgenis.data.annotation.makervcf.positionalstream.MatchVariantsToGenotypeAndInheritance;
import org.molgenis.data.annotation.makervcf.structs.Relevance;
import org.molgenis.data.annotation.makervcf.structs.RelevantVariant;
import org.molgenis.data.annotation.makervcf.structs.TrioData;
import org.molgenis.data.vcf.datastructures.Trio;
import org.molgenis.data.annotation.makervcf.positionalstream.MatchVariantsToGenotypeAndInheritance.status;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.molgenis.data.vcf.utils.VcfUtils;
import org.molgenis.data.vcf.utils.VcfWriterUtils;


/**
 * Created by joeri on 6/29/16.
 *
 * find and mark denovo
 * remove variants that are no longer relevant, ie that have an equal parental genotype
 *
 * default behaviour: any parent is considered to be healthy, and will be removed (!!)
 * as well as any children that share one or both of their parents genotypes, including IBS (e.g. 0|1 and 1|0 are equal)
 *
 *
 * TODO
    drop ? update?
 //todo: swapped genotypes (phases)
 //todo: also remove parent(s) !!
 *
 */
public class TrioFilter extends GeneStream{


    private HashMap<String, Trio> trios;
    private Set<String> parents;

    public TrioFilter(Iterator<RelevantVariant> relevantVariants, TrioData td, boolean verbose) throws Exception {
        super(relevantVariants, verbose);
        this.trios = td.getTrios();
        this.parents = td.getParents();
        if(verbose){ System.out.println("[TrioFilter] Trios: " + trios.toString()); }
        if(verbose){ System.out.println("[TrioFilter] Parents: " + parents.toString()); }

    }


    public static TrioData getTrioData(File inputVcfFile) throws Exception {
        BufferedReader bufferedVCFReader = VcfWriterUtils.getBufferedVCFReader(inputVcfFile);
        HashMap<String, Trio> trios = VcfUtils.getPedigree(bufferedVCFReader);

        Set<String> parents = new HashSet<>();
        for(String child : trios.keySet())
        {
            String momId = trios.get(child).getMother() != null ? trios.get(child).getMother().getId() : null;
            String dadId = trios.get(child).getFather() != null ? trios.get(child).getFather().getId() : null;
            if(momId != null){ parents.add(momId); }
            if(dadId != null){ parents.add(dadId); }
        }
        for(String child : trios.keySet())
        {
            if(parents.contains(child))
            {
                throw new Exception("Trio child '" + child + "' is also a parent. Complex pedigrees currently not supported.");
            }
        }

        return new TrioData(trios, parents);
    }

    @Override
    public void perGene(String gene, List<RelevantVariant> variantsPerGene) throws Exception {

        if(verbose) { System.out.println("[TrioFilter] Encountered gene: " + gene); }

        for(RelevantVariant rv : variantsPerGene) {

            for (Relevance rlv : rv.getRelevance())
            {
                if(!rlv.getGene().equals(gene))
                {
                    continue;
                }

                if (verbose) {
                    System.out.println("[TrioFilter] Encountered variant: " + rlv.toString());
                }

                Set<String> samplesToRemove = new HashSet<>();
                char affectedIndex = Character.forDigit(rv.getVariant().getAltIndex(rlv.getAllele()), 10);

                for (String sample : rlv.getSampleStatus().keySet()) {

                    if (verbose) {
                        System.out.println("[TrioFilter] Encountered sample: " + sample);
                    }

                    boolean isParent = parents.contains(sample);

                    if (isParent) {
                        if (verbose) {
                            System.out.println("[TrioFilter] Sample is parent! dropping");
                        }

                        samplesToRemove.add(sample);
                        continue;
                    }
                    // is a child, check if relevant genotype
                    else if (trios.containsKey(sample)) {
                        String childGeno = rlv.getSampleGenotypes().get(sample);
                        String momId = trios.get(sample).getMother() != null ? trios.get(sample).getMother().getId() : null;
                        String motherGeno = rlv.getSampleGenotypes().get(momId);
                        String dadId = trios.get(sample).getFather() != null ? trios.get(sample).getFather().getId() : null;
                        String fatherGeno = rlv.getSampleGenotypes().get(dadId);

//                    if(fatherGeno == null && motherGeno == null)
//                    {
//                        if(verbose) { System.out.println("[TrioFilter] no parental genotypes, skipping sample " + sample); }
//                        continue;
//                    }

                        boolean childHomoOrHemizygous = childGeno.equals(affectedIndex + "/" + affectedIndex) || childGeno.equals(affectedIndex + "|" + affectedIndex) || childGeno.equals(affectedIndex + "");
                        boolean childHeterozygous = childGeno.length() == 3 && StringUtils.countMatches(childGeno, affectedIndex + "") == 1;

                        boolean fatherHomoOrHemizygous = false;
                        boolean fatherHeterozygous = false;
                        if (fatherGeno != null) {
                            fatherHomoOrHemizygous = fatherGeno.equals(affectedIndex + "/" + affectedIndex) || fatherGeno.equals(affectedIndex + "|" + affectedIndex) || fatherGeno.equals(affectedIndex + "");
                            fatherHeterozygous = fatherGeno.length() == 3 && StringUtils.countMatches(fatherGeno, affectedIndex + "") == 1;
                        }
                        boolean fatherReference = rlv.getParentsWithReferenceCalls().contains(dadId) ? true : false;

                        boolean motherHomoOrHemizygous = false;
                        boolean motherHeterozygous = false;
                        if (motherGeno != null) {
                            motherHomoOrHemizygous = motherGeno.equals(affectedIndex + "/" + affectedIndex) || motherGeno.equals(affectedIndex + "|" + affectedIndex) || motherGeno.equals(affectedIndex + "");
                            motherHeterozygous = motherGeno.length() == 3 && StringUtils.countMatches(motherGeno, affectedIndex + "") == 1;
                        }
                        boolean motherReference = rlv.getParentsWithReferenceCalls().contains(momId) ? true : false;
                        ;


                        if (verbose) {
                            System.out.println("[TrioFilter] Child " + sample + " has genotype " + childGeno + " mom: " + (motherReference ? "REFERENCE" : motherGeno) + ", dad: " + (fatherReference ? "REFERENCE" : fatherGeno));
                        }

                        /**
                         * cases where child shares a genotype with 1 or both parents, thus removing this not relevant sample
                         */
                        if (childHomoOrHemizygous && (fatherHomoOrHemizygous || motherHomoOrHemizygous)) {
                            if (verbose) {
                                System.out.println("[TrioFilter] Child " + sample + " homozygous genotype " + childGeno + " with at least 1 homozygous parent, mom: " + motherGeno + ", dad: " + fatherGeno);
                            }
                            samplesToRemove.add(sample);
                            continue;

                        } else if (childHeterozygous && (fatherHeterozygous || motherHeterozygous || fatherHomoOrHemizygous || motherHomoOrHemizygous)) {
                            if (verbose) {
                                System.out.println("[TrioFilter] Child " + sample + " heterozygous genotype " + childGeno + " with at least 1 heterozygous parent, mom: " + motherGeno + ", dad: " + fatherGeno);
                            }
                            samplesToRemove.add(sample);
                            continue;
                        }

                        /**
                         * cases of regular inheritance where the child is still interesting, just to catch and clarify them
                         */
                        if (childHomoOrHemizygous && ((motherHeterozygous && fatherHeterozygous) || (motherHeterozygous && fatherGeno == null) || (motherGeno == null && fatherHeterozygous))) {
                            //genotype 1|1 mom: 1|0, dad: 1|0
                            //genotype 1|1 mom: 1|0, dad: null
                            //either matches inheritance and relevant, or we're not sure if one of the parents was hetero- or homozygous
                            continue;
                        } else if (childHeterozygous && ((fatherGeno == null && motherReference) || (motherGeno == null && fatherReference))) {
                            //genotype 0|1 mom: null, dad: REFERENCE
                            //genotype 1|0 mom: REFERENCE, dad: null
                            //could still be relevant, since we miss a genotype and don't know if we can call denovo or filter out
                            continue;
                        } else if (fatherGeno == null && motherGeno == null) {
                            //genotype 1|1 mom: null, dad: null
                            //both parents missing, cant really do much, so leave it
                            continue;
                        }


                        /**
                         * cases of de novo where where child has unexplained genotype
                         */
                        if (childHomoOrHemizygous && ((motherHeterozygous && fatherReference) || (fatherHeterozygous && motherReference) || (fatherReference && motherReference))) {
                            if (verbose) {
                                System.out.println("[TrioFilter] De novo homozygous variant for child " + sample + " heterozygous genotype " + childGeno + ", mom: " + (motherReference ? "REFERENCE" : motherGeno) + ", dad: " + (fatherReference ? "REFERENCE" : fatherGeno));
                            }
                            //right now, don't do anything special with the knowledge that this is (suspected) de novo variant
                            continue;
                        } else if (childHeterozygous && fatherReference && motherReference) {
                            if (verbose) {
                                System.out.println("[TrioFilter] De novo heterozygous variant for child " + sample + " heterozygous genotype " + childGeno + ", mom: " + (motherReference ? "REFERENCE" : motherGeno) + ", dad: " + (fatherReference ? "REFERENCE" : fatherGeno));
                            }
                            //right now, don't do anything special with the knowledge that this is (suspected) de novo variant
                            continue;
                        }

                        //we don't expect to get here..
                        System.out.println("[TrioFilter] WARNING: Unexpected genotypes, please check: child " + sample + " has genotype " + childGeno + " mom: " + (motherReference ? "REFERENCE" : motherGeno) + ", dad: " + (fatherReference ? "REFERENCE" : fatherGeno));

                    } else {
                        if (verbose) {
                            System.out.println("[TrioFilter] Sample not part of a trio: " + sample + ", ignoring");
                        }

                    }
                }

                for (String sample : samplesToRemove) {
                    if (verbose) {
                        System.out.println("[TrioFilter] Removing sample: " + sample);
                    }
                    rlv.getSampleStatus().remove(sample);
                    rlv.getSampleGenotypes().remove(sample);
                }
            }
        }
    }
}
