"
Usage: script.R [--input=<jsonFile>] [--output=<rdsFile>]

Options:
  -i --input=<jsonFile>  Input file in JSON format [default: test.json]
  -o --output=<rdsFile>  Output file in RDS (R serialized data) format [default: data.rds]

" -> doc

# attach libraries
library(docopt)
library(jsonlite)
library(tidyr)
library(plyr)

# parse args
args <- docopt(doc)

# load data (flattened)
data.flat <- fromJSON(args$input, flatten = TRUE)

# drop weights (FIXME: hardcoded!)
data.flat$phonemeWeights.natural <- NULL
data.flat$phonemeWeights.mngu0_weights <- NULL
data.flat$phonemeWeights.mngu0_weights_dnn <- NULL

# restructure data
data <- data.flat %>%
  gather(key, value, -utterance, -frame, -time, -phone, -phone_class) %>%
    separate(key, into = c("ignore", "coil", "axis", "type"), sep = "\\.")
# drop redundant column
data$ignore <- NULL

# convert strings to factors, tweak names
data$utterance <- as.factor(data$utterance)
data$phone <- as.factor(data$phone)
data$phone_class<- as.factor(data$phone_class)
data$coil <- as.factor(data$coil)
data$axis <- revalue(as.factor(data$axis), c("distances" = "distance"))
data$type <- revalue(as.factor(data$type), c("euclidian" = "Euclidean"))

# serialize to file
saveRDS(data, args$output)
