#!/usr/bin/python
import time
import subprocess
import sys
import os
import shutil
from time import gmtime, strftime, localtime
from datetime import datetime

##   Extraction values
def createSummaries(extractVal, metrics, dire):
    cmd=""
    for alg in os.listdir(dire):
        if alg in extractVal:
            if os.path.exists(dire+alg+"/summaries/"):
	            shutil.rmtree(dire+alg+"/summaries/")
            os.makedirs(dire+alg+"/summaries/")
            for given in os.listdir(dire+alg):
	            if "given" in given: 
		            with open(dire+alg+"/summaries/"+given+".summary", "a") as myfile:
                			myfile.write("\nResult Top 50 \n")
		            cmd = "cat "+dire+alg+"/"+given+"/top_50/"+"metrics.complete"
		            cmd += " >> "+dire+alg+"/summaries/"+given+".summary"
    	            subprocess.call(cmd, shell=True)    

            for metric in metrics:
                if metric == 'alpha-nDCG' or metric == 'P-IA':
                    valor=["5","10","20"]
                else:
                    valor=["5","10","15","20"]
                for elem in valor:
                    extractResult(metric,elem,dire,alg)
                cmd ="awk '1;!(NR%6){print \" \";}' "+dire+alg+"/summaries/"+metric+"Temp > "+dire+alg+"/summaries/"+metric+"Sum"
                subprocess.call(cmd, shell=True)
                cmd ="sed -i 's/\\./,/' "+dire+alg+"/summaries/"+metric+"Sum"
                subprocess.call(cmd, shell=True)
                cmd ="rm "+dire+alg+"/summaries/"+metric+"Temp"
                subprocess.call(cmd, shell=True)
            print time.strftime("%Y-%m-%d %H:%M") + " Summaries avg "+ alg + " completed."
    print time.strftime("%Y-%m-%d %H:%M") + " Summaries avg are completed."
    createSummariesALL(extractVal, metrics, dire)

def createSummariesALL(extractVal, metrics, dire):
    cmd=""
    for alg in os.listdir(dire):
        if alg in extractVal:
            if not(os.path.exists(dire+alg+"/summaries/")):
                os.makedirs(dire+alg+"/summaries/")
            for given in os.listdir(dire+alg):
	            if "given" in given: 
		            with open(dire+alg+"/summaries/"+given+".summaryALL", "a") as myfile:
                			myfile.write("\nResult Top 50 \n")
		            cmd = "cat "+dire+alg+"/"+given+"/top_50/"+"metrics.completeALL"
		            cmd += " | grep -v 'null' > "+dire+alg+"/summaries/"+given+".summaryALL"
    	            subprocess.call(cmd, shell=True)    

            for metric in metrics:
                valor=["5","10","20"]
                for elem in valor:
                    extractResultALL(metric,elem,dire,alg)
                for sparsity in valor:
                    cmd ="cat "+dire+alg+"/summaries/result"+metric+sparsity+"ALL | grep '5 ' | awk 'BEGIN { FS = \" \"};{ print $2 }' > "+dire+alg+"/summaries/result"+metric+"_Top_"+sparsity+"given_5.ALL" 
                    subprocess.call(cmd, shell=True)
                    cmd ="cat "+dire+alg+"/summaries/result"+metric+sparsity+"ALL | grep '20 ' | awk 'BEGIN { FS = \" \"};{ print $2 }' > "+dire+alg+"/summaries/result"+metric+"_Top_"+sparsity+"given_20.ALL" 
                    subprocess.call(cmd, shell=True)
                    cmd ="cat "+dire+alg+"/summaries/result"+metric+sparsity+"ALL | grep '100 ' | awk 'BEGIN { FS = \" \"};{ print $2 }' > "+dire+alg+"/summaries/result"+metric+"_Top_"+sparsity+"given_all.ALL" 
                    subprocess.call(cmd, shell=True)
            print time.strftime("%Y-%m-%d %H:%M") + " Summaries total "+ alg + " completed."
    print time.strftime("%Y-%m-%d %H:%M") + " Summaries total are completed."

##   Extract result from file
def extractResult(metric,elem,dire,alg):
    cmd = "grep \""+metric+"_"+elem+"=\" "+dire+alg+"/summaries/*.summary >> "+dire+alg+"/summaries/res"+metric+elem+".sum1"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\ "+dire+alg+"/summaries/given_\ \ ' "+dire+alg+"/summaries/res"+metric+elem+".sum1"
    subprocess.call(cmd, shell=True)
    cmd ="cat "+dire+alg+"/summaries/res"+metric+elem+".sum1 | awk 'BEGIN { FS = \"given_\"};{ print $2 }'| uniq > "+dire+alg+"/summaries/res"+metric+elem+".sum"
    subprocess.call(cmd, shell=True)
    cmd ="rm "+dire+alg+"/summaries/res"+metric+elem+".sum1"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\.summary:"+metric+"_"+elem+"=\ \ ' "+dire+alg+"/summaries/res"+metric+elem+".sum"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\\all\\100\ ' "+dire+alg+"/summaries/res"+metric+elem+".sum"
    subprocess.call(cmd, shell=True)
    cmd ="sort -g "+dire+alg+"/summaries/res"+metric+elem+".sum > "+dire+alg+"/summaries/result"+metric+elem
    subprocess.call(cmd, shell=True)
    cmd ="rm "+dire+alg+"/summaries/res"+metric+elem+".sum"
    subprocess.call(cmd, shell=True)
    cmd ="cat "+dire+alg+"/summaries/result"+metric+elem+" | awk 'BEGIN { FS = \" \"};{ print $2 }' >> "+dire+alg+"/summaries/"+metric+"Temp"
    subprocess.call(cmd, shell=True)

##   Extract result from file
def extractResultALL(metric,elem,dire,alg):
    cmd = "grep \""+metric+"_"+elem+"=\" "+dire+alg+"/summaries/*.summaryALL >> "+dire+alg+"/summaries/res"+metric+elem+".sum1ALL"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\ "+dire+alg+"/summaries/given_\ \ ' "+dire+alg+"/summaries/res"+metric+elem+".sum1ALL"
    subprocess.call(cmd, shell=True)
    cmd ="cat "+dire+alg+"/summaries/res"+metric+elem+".sum1ALL | awk 'BEGIN { FS = \"given_\"};{ print $2 }' > "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
    subprocess.call(cmd, shell=True)
    cmd ="rm "+dire+alg+"/summaries/res"+metric+elem+".sum1ALL"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\.summaryALL:\ \ ' "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\\"+metric+"_"+elem+"=\ \ ' "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
    subprocess.call(cmd, shell=True)
    cmd ="mv "+dire+alg+"/summaries/res"+metric+elem+".sumALL "+dire+alg+"/summaries/res"+metric+elem+".sumALLTEMP"
    subprocess.call(cmd, shell=True)
    cmd ="cat "+dire+alg+"/summaries/res"+metric+elem+".sumALLTEMP | awk 'BEGIN { FS = \" \"};{ print $1 (\" \") $3 }' > "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
    subprocess.call(cmd, shell=True)
    cmd ="rm "+dire+alg+"/summaries/res"+metric+elem+".sumALLTEMP"
    subprocess.call(cmd, shell=True)
    cmd ="sed -i 's\\all\\100\ ' "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
    subprocess.call(cmd, shell=True)
#    cmd ="sed -i 's/\\./,/' "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
#    subprocess.call(cmd, shell=True)
    cmd ="sort -g "+dire+alg+"/summaries/res"+metric+elem+".sumALL > "+dire+alg+"/summaries/result"+metric+elem+"ALL"
    subprocess.call(cmd, shell=True)
    cmd ="rm "+dire+alg+"/summaries/res"+metric+elem+".sumALL"
    subprocess.call(cmd, shell=True)