/*
 *  Copyright (c) The University of Sheffield.
 *
 *  This file is free software, licensed under the 
 *  GNU Library General Public License, Version 2.1, June 1991.
 *  See the file LICENSE.txt that comes with this software.
 *
 */
package gate.plugin.tagger.corenlp;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Controller;
import gate.Document;
import gate.Factory;
import gate.FeatureMap;
import gate.Utils;
import gate.creole.metadata.CreoleParameter;
import gate.creole.metadata.CreoleResource;
import gate.creole.metadata.Optional;
import gate.creole.metadata.RunTime;
import gate.util.GateRuntimeException;
import java.util.ArrayList;
import java.util.List;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;


/**
 * Processing resource for using the CoreNLP server.
 * 
 * @author Johann Petrak
 */
@CreoleResource(name = "Tagger_CoreNLP",
        comment = "Annotate documents using a CoreNLP server",
        // icon="taggerIcon.gif",
        helpURL = "https://github.com/GateNLP/gateplugin-Tagger_CoreNLP/wiki/Tagger_CoreNLP"
)
public class TaggerCoreNLP extends AbstractDocumentProcessor {

  // PR PARAMETERS
  protected String containingAnnotationType = "";

  @CreoleParameter(comment = "The annotation that covers the document text to annotate", defaultValue = "")
  @RunTime
  @Optional
  public void setContainingAnnotationType(String val) {
    containingAnnotationType = val;
  }

  public String getContainingAnnotationType() {
    return containingAnnotationType;
  }

  protected String inputAnnotationSet = "";
  @CreoleParameter(comment = "The input annotation set", defaultValue = "")
  @RunTime
  @Optional
  public void setInputAnnotationSet(String val) {
    inputAnnotationSet = val;
  }

  public String getInputAnnotationSet() {
    return inputAnnotationSet;
  }
  
  protected String outputAnnotationSet = "";
  @CreoleParameter(comment = "The output annotation set", defaultValue = "")
  @RunTime
  @Optional
  public void setOutputAnnotationSet(String val) {
    outputAnnotationSet = val;
  }

  public String getOutputAnnotationSet() {
    return outputAnnotationSet;
  }
 
  public String serverUrl = "http://127.0.0.1:9000";
  @CreoleParameter(comment = "The CoreNLP server address",defaultValue = "http:127.0.0.1:9000")
  @RunTime
  @Optional
  public void setServerUrl(String val) {
    serverUrl = val;
  }
  public String getServerUrl() { return serverUrl; }
  
  public FeatureMap properties = Factory.newFeatureMap();
  @CreoleParameter(comment = "The CoreNLP properties settings to send",defaultValue = "")
  @RunTime
  @Optional
  public void setProperties(FeatureMap val) {
    properties = val;
  }
  public FeatureMap getProperties() { return properties; }

  // FIELDS

 
  // HELPER METHODS


  @Override
  protected Document process(Document document) {
    //System.err.println("DEBUG: processing document "+document.getName());
    if(isInterrupted()) {
      interrupted = false;
      throw new GateRuntimeException("Processing has been interrupted");
    }
    
    // From tests I could not find an easy way to map multiple texts sent to the service
    // to the multiple responses (for each sentence) we get back. So we could add three texts
    // and then get back 8 sentences and the token offsets for each sentence would be relative
    // to the start of the sentence. 
    
    if(getContainingAnnotationType() != null && !getContainingAnnotationType().isEmpty()) {
      AnnotationSet anns = document.getAnnotations(getInputAnnotationSet()).get(getContainingAnnotationType());
      //System.err.println("DEBUG: containing annotations: "+anns.size());
      for(Annotation ann : anns) {
        String text = gate.Utils.stringFor(document, ann);
        processSpan(document,text,Utils.start(ann).intValue());
      }
    } else {
      String text = document.getContent().toString();
      processSpan(document,text,0);
    }
    return document;
  }
  
  public void processSpan(Document document, String text, int spanOffset) {
    

  }
  

  @Override
  protected void beforeFirstDocument(Controller ctrl) {
  }

  @Override
  protected void afterLastDocument(Controller ctrl, Throwable t) {
  }

  @Override
  protected void finishedNoDocument(Controller ctrl, Throwable t) {
  }

}
