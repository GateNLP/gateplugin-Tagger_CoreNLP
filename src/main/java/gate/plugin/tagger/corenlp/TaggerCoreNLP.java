/*
 *  Copyright (c) The University of Sheffield.
 *
 *  This file is free software, licensed under the 
 *  GNU Library General Public License, Version 2.1, June 1991.
 *  See the file LICENSE.txt that comes with this software.
 *
 */
package gate.plugin.tagger.corenlp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;


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
 
  public String serverUrl = "http://127.0.0.1:9000/";
  @CreoleParameter(comment = "The CoreNLP server address",defaultValue = "http://127.0.0.1:9000/")
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
  
  // This sends some text over to the CoreNLP server, gets back the result
  // as JSON and uses the JSON data to create annotations.
  public void processSpan(Document document, String text, int spanOffset) {
    AnnotationSet outset = document.getAnnotations(getOutputAnnotationSet());
    // this is the data we will send over to the server
    Map data4json = new HashMap<String,Object>();
    FeatureMap props = getProperties();
    if(props == null || props.isEmpty()) {
      data4json.put("annotators","tokenize, ssplit, pos, ner, parse");      
      data4json.put("tokenize.language","en");
    } else {
      data4json.putAll(props);
    }
    String json = null;
    ObjectMapper mapper = new ObjectMapper();
    try {
      json = mapper.writeValueAsString(data4json);
    } catch (JsonProcessingException ex) {
      throw new GateRuntimeException("Could not convert parameters to json",ex);
    }
    //System.err.println("GOT JSON: "+json);
    
    HttpResponse<String> response;
    try {
      response = Unirest.post(serverUrl)
              .header("accept","application/json")
              .queryString("properties", json)
              .body(text)
              .asString();
    } catch (UnirestException ex) {
      throw new GateRuntimeException("Exception when connecting to the server",ex);
    }

    // The response should be either OK and JSON or not OK and an error message
    int status = response.getStatus();
    if(status != 200) {
      throw new GateRuntimeException("Response von server is NOK, status="+status+" msg="+response.getBody());
    }
    String responseString = response.getBody();
    // sometimes the server seems to return 0 codes in the string which the 
    // jackson parser does not accept, so lets just remove them
    //System.err.println("Got response, status is OK, data is: "+responseString);    
    responseString = responseString.replaceAll("\0", "");
    Map responseMap = null;
    try {
      // Parse the json
      responseMap = mapper.readValue(responseString, HashMap.class);
    } catch (IOException ex) {
      throw new GateRuntimeException("Could not read the response",ex);
    }
    // The map may contain the following fields, depending on what we wanted CoreNLP to do:
    // sentences: an array of maps, each inner maps has
    //   index: int
    //   parse: could be the constant string "SENTENCE_SKIPPED_OR_UNPARSABLE" or the parse
    //     in conventional format: (ROOT\n  (S\n    (NP (NNP George)
    //   tokens: an array of tokens of the following form
    //      index: int
    //      word: String
    //      characterOffsetEnd: int
    //      ner: entity type or "O" 
    //      characterOffsetBegin: int
    //      lemma: String
    //      originalText: String (not sure what is the difference to word)
    //      pos: the pos tag, String
    //      before/after: not sure what these are for, always empty in my tests
    //   Offsets continue accross sentences and seem to count whitespace correctly.
    
    List<Map<String,Object>> sentences = (List<Map<String,Object>>)responseMap.get("sentences");
    for(Map<String,Object> sentence : sentences) {
      // get the tokens
      List<Map<String,Object>> tokens = (List<Map<String,Object>>)sentence.get("tokens");
      // we need to create annotations for ners across tokens, so we remember
      // the last type and last starting offset. If the type is "O" there is no
      // current NE. We create a new annotation whenever we get a different type and the 
      // last type was not an O plus after the sentence when the last type was 
      // not an O
      String lastType = "O";
      int lastBegin = -1;
      int lastEnd = -1;
      // we also remember the begin offset of the first token as sentenceBegin
      int sentenceBegin = -1;
      int sentenceEnd = -1;
      String ner = null;
      for(Map<String,Object> token : tokens) {
        int begin = (Integer)token.get("characterOffsetBegin");
        int end = (Integer)token.get("characterOffsetEnd");
        int index = (Integer)token.get("index");
        ner = (String)token.get("ner");
        String pos = (String)token.get("pos");
        String lemma = (String)token.get("lemma");
        
        if(sentenceBegin == -1) {
          sentenceBegin = begin;
        }
        sentenceEnd = end;
        // create the token annotation
        FeatureMap fm = Factory.newFeatureMap();
        if(pos!=null) fm.put("category", pos);
        if(lemma!=null) fm.put("root",lemma);
        fm.put("index",index);
        if(ner!=null) fm.put("ner",ner);
        Utils.addAnn(outset, spanOffset+begin, spanOffset+end, "Token", fm);
        
        if(ner != null) {
          // we started a NE in the past and now have either a different one or an O
          // we reset the last NE info.
          if(!lastType.equals("O") && !lastType.equals(ner)) {
            Utils.addAnn(outset, spanOffset+lastBegin, spanOffset+lastEnd, lastType, Utils.featureMap());
            lastBegin = -1;
            lastType = "O";
          }
          // if we currently do have a NE then if this is the first, remember the begin 
          // always remember the end
          if(!ner.equals("O")) {
            if(lastBegin == -1) lastBegin = begin;
            lastEnd = end;
          }
          // the last type is set always
          lastType = ner;
        }
      }
      // we processed a sentence, now create the sentence annotation
      Utils.addAnn(outset, spanOffset+sentenceBegin, spanOffset+sentenceEnd, "Sentence", Utils.featureMap());
      // also create an NE annotation if one is still outstanding
      if(lastType != null && !lastType.equals("O")) {
        Utils.addAnn(outset, spanOffset+lastBegin, spanOffset+lastEnd, ner, Utils.featureMap());
      }
    } // for sentence : sentences
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
