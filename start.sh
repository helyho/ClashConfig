#!/bin/bash
java -cp $(for i in lib/*.jar ; do echo -n $i: ; done). org.clash.config.App