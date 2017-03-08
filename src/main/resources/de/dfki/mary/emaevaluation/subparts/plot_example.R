"
Usage: script.R [--input=<dataFile>] [--output=<texFile>] [--labfilename=<labfilename>] [--utterance=<utterance>] [--width=<w>] [--height=<h>]

Options:
  -i --input=<dataFile>  Input file in RDS (R serialized data) format [default: data.rds]
  -o --output=<texFile>  Output file in LaTeX (TikZ) format [default: figure.tex]
  -l --labfilename=<labfilename>   monophone label in HTK format
  -u --utterance=<utt>   utterance to process [default: mngu0_s1_0016]
  -w --width=<w>         Output width in inches [default: 7]
  -h --height=<h>        Output height in inches [default: 4]

" -> doc

# attach libraries
library(docopt)
library(ggplot2)
library(tikzDevice)

# parse args
args <- docopt(doc)

# load data
data.all <- readRDS(args$input)
data <- subset(data.all,
  utterance == args$utterance &
  coil %in% c("T1", "T2", "T3"))

# Load segmentation
lab_filename <- args$labfilename
segments <- read.table(lab_filename, header=FALSE)
colnames(segments) <- c("xmin","xmax","label")

# Conversion of boundaries in seconds (htk default unit is 100nanos)
segments$xmin <- segments$xmin/10000000
segments$xmax <- segments$xmax/10000000

# Some classification to visualize interesting events [FIXME: kind of hardcoded...]
segments$class <- "useless"
if (nrow(segments[segments$label %in% c("pau"),]) > 0) {
    segments[segments$label %in% c("pau"),]$class <-  "pause"
}
if (nrow(segments[segments$label %in% c("d", "dh", "t", "s", "z", "n"),]) > 0) {
    segments[segments$label %in% c("d", "dh", "t", "s", "z", "n"),]$class <-  "coronal consonant"
}
if (nrow(segments[segments$label %in% c("g"),]) > 0) {
    segments[segments$label %in% c("g"),]$class <-  "dorsal consonant"
}
int_seg <- subset(segments, !(class %in% c("useless")))

# plot
p <- ggplot(data) +
    scale_colour_manual(values=c("red", "blue", "green"),
                        labels=c("Euclidean distance", "predicted", "observed")) +
    geom_line(aes(x=time, y=value, color = type)) +
    scale_fill_brewer(palette="Set1") +
    geom_rect(inherit.aes = FALSE,
              data = int_seg,
              aes(xmin=xmin, xmax=xmax, ymin = -Inf, ymax = Inf, fill=class),
              alpha=0.2) +
    facet_grid(axis ~ coil, scales = "free_y") +
    labs(x = "Time (s)", y = "Position (cm)") +
    theme_bw() +
    theme(legend.position = "bottom", legend.box = "horizontal")

# output
options(tikzDocumentDeclaration = "\\documentclass{IEEEtran}\n")
tikz(args$output, sanitize = TRUE, standAlone = TRUE, width = args$width, height = args$height)
print(p)
dev.off()
