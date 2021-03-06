package org.molgenis.data.annotation.makervcf.genestream.impl;

import org.molgenis.data.annotation.makervcf.genestream.core.GeneStream;
import org.molgenis.data.annotation.makervcf.structs.Relevance;
import org.molgenis.data.annotation.makervcf.structs.RelevantVariant;

import java.util.Iterator;
import java.util.List;

/**
 * Created by joeri on 6/29/16.
 *  take on MANTA / DELLY output
 *  annotate, elevate carrier status to affected when SV on same location
 *
 *  TODO
 *
 */
public class CombineWithSVcalls extends GeneStream{

    private Iterator<RelevantVariant> relevantVariants;
    private boolean verbose;

    public CombineWithSVcalls(Iterator<RelevantVariant> relevantVariants, boolean verbose)
    {
        super(relevantVariants, verbose);
    }

    @Override
    public void perGene(String gene, List<RelevantVariant> variantsPerGene) throws Exception {

        for(RelevantVariant rv: variantsPerGene) {
            for (Relevance rlv : rv.getRelevance()) {
                if (!rlv.getGene().equals(gene)) {
                    continue;
                }
                for (String sample : rlv.getSampleStatus().keySet()) {

                }
            }
        }
    }


}
