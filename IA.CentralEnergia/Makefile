# Makefile for Practica 1 IA (local search)
#
#   make                      compile everything into build/
#   make run EXP=1            run experiment 1 and save the results in results/exp1.csv
#   make run EXP=2 REPS=3     run experiment 2 with 3 repetitions
#   make test                 single quick run (experiment 0), printed on screen
#   make clean                remove build/ (keeps results/)
#
# EXP can be 0, 1, 2, 3, 4a, 4b, 5 or 6.

# Libraries (paths relative to this folder; override them if your layout is different,
# e.g. make CENTRAL_JAR=/path/CentralEnergia.jar)
CENTRAL_JAR = ../CentralEnergia/CentralEnergia.jar
AIMA_JAR    = ../dist/AIMA.jar

BUILD   = build
RESULTS = results
CP      = $(CENTRAL_JAR):$(AIMA_JAR)

JAVAC      = javac
JAVA       = java
JAVA_FLAGS = -Xmx4g

EXP  = 0
REPS = 10

SOURCES = $(wildcard *.java)

.PHONY: all run test clean

all: $(BUILD)/.compiled

# Recompiles all the sources whenever one of them changes
$(BUILD)/.compiled: $(SOURCES)
	@mkdir -p $(BUILD)
	$(JAVAC) -cp $(CP) -d $(BUILD) $(SOURCES)
	@touch $@

run: all
	@mkdir -p $(RESULTS)
	$(JAVA) $(JAVA_FLAGS) -cp $(CP):$(BUILD) Main $(EXP) $(REPS) > $(RESULTS)/exp$(EXP).csv
	@echo "Results saved in $(RESULTS)/exp$(EXP).csv"

test: all
	$(JAVA) $(JAVA_FLAGS) -cp $(CP):$(BUILD) Main 0

clean:
	rm -rf $(BUILD)
