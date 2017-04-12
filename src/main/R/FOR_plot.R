library(ggplot2)

# load FOR and CGD sets
falseOmRate <- read.table("/Users/joeri/Desktop/GAVIN-APP/1000G_diag_FDR/FOR/FOR_results_per_gene.tsv", sep="\t", header=T)
cgd <- read.table("/Users/joeri/github/rvcf/src/test/resources/GenesInheritance31aug2016.tsv", sep="\t", header=T)

# merge, keeping only CGD genes
df <- merge(falseOmRate, cgd, by = "Gene", all.x = TRUE)
df <- df[!is.na(df$Inheritance),]

# go go gadget ggplot
ggplot() +
  theme_bw() + theme(panel.grid.major = element_line(colour = "black"), axis.text=element_text(size=16),  axis.title=element_text(size=16,face="bold")) +
  geom_point(data = df, aes(x = Expected, y = Expected-Observed, shape = Inheritance, colour = Inheritance), size=3, stroke = 3, alpha=0.75) +
  geom_text(data = df, aes(x = Expected, y = Expected-Observed, label = Gene), hjust = 0, nudge_x = 0.01, size = 3, check_overlap = TRUE) +
  ylab("Variants expected-observed (i.e. missed)") +
  xlab("Variants expected (i.e. total per gene)")

ggsave("FOR_plot.pdf", width = 8, height = 6)