package org.neo4j.example.unmanagedextension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.map.ObjectMapper;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexManager;
import org.neo4j.helpers.collection.IteratorUtil;
import org.neo4j.server.database.CypherExecutor;

import sun.misc.BASE64Encoder;


@Path("/nested")
public class MyService {
    List<Object> listeners=new ArrayList<>();
    enum Labels implements Label {
People,Comments
}
    enum RelType implements RelationshipType{
        KNOWS,NOTIFIES,PARENT
    }
 
    
    
    @GET
    @Path("/createComment")
    @Produces("application/json")
    public void createComment(@QueryParam("comment") String comment,@Context GraphDatabaseService db)
    {
        Transaction tx=db.beginTx();
        Node a=db.createNode();
        a.setProperty("comment", comment);
        a.addLabel(Labels.Comments);
        tx.success();
        
    }
    @GET 
    @Path("/writeReply")
    @Produces("application/json")
    public void writeReply(@QueryParam("id") long l,@QueryParam("reply") String reply,@Context GraphDatabaseService db)
    {
       Transaction tx=db.beginTx();
       Node replyNode=db.createNode(Labels.Comments);
       replyNode.setProperty("reply", reply);
       Node a=db.getNodeById(l);
       a.createRelationshipTo(replyNode, RelType.PARENT);
       tx.success();
    }
    @GET
    @Path("/deleteReply")
    @Produces("application/json")
    public void deleteReply(@QueryParam("id") long l,@Context GraphDatabaseService db)
    {
        Transaction tx=db.beginTx();
        Node deleteNode=db.getNodeById(l);
        Relationship r=deleteNode.getSingleRelationship(RelType.PARENT, Direction.INCOMING);
        Node parentNode=r.getOtherNode(deleteNode);
        r.delete();
        Iterator<Relationship> rels=deleteNode.getRelationships(RelType.PARENT, Direction.OUTGOING).iterator();
        ArrayList<Node> childs=new ArrayList<>();
        
        while(rels.hasNext())
        {
            Relationship r1=rels.next();
            Node k=r1.getOtherNode(deleteNode);
            r1.delete();
            childs.add(k);
        }
        deleteNode.delete();
        for(Node n:childs)
        {
            parentNode.createRelationshipTo(n, RelType.PARENT);
        }
        tx.success();
    }
    @GET 
    @Path("/genToken/{name}")
    @Produces("application/json")
    public Response genToken(@PathParam("name") String name,@Context GraphDatabaseService db) throws IOException
    {
        Map <String,Object> mapper=new HashMap<>();
        Transaction tx=db.beginTx();
        ResourceIterator<Node> iter=db.findNodesByLabelAndProperty(Labels.People, "name", name).iterator();
        while(iter.hasNext())
        {
            Node a=iter.next();
            Date d=new Date();
            String key=a.getProperty("name").toString()+a.getProperty("passWord")+d.getTime();
            key=new BASE64Encoder().encode(key.getBytes());
            a.setProperty("oauth_token", key);
            mapper.put(name,key );
        }
        tx.success();
        return Response.ok().entity(new ObjectMapper().writeValueAsString(mapper)).build();
    }
       @GET
       @Path("/me/all")
       @Produces("application/json")
       public Response notifyToAll(@QueryParam("message") String message,@QueryParam("auth") String token,@Context GraphDatabaseService db)
               
       {
           Transaction tx=db.beginTx();
           ResourceIterator<Node> personIterator=db.findNodesByLabelAndProperty(Labels.People, "oauth_token", token).iterator();
           while(personIterator.hasNext())
           {
               Node person=personIterator.next();
               Iterator<Relationship> iterRelationship=person.getRelationships(Direction.BOTH, RelType.KNOWS).iterator();
               while(iterRelationship.hasNext())
               {
                   Node b=iterRelationship.next().getOtherNode(person);
                   Relationship r=b.createRelationshipTo(person, RelType.NOTIFIES);
                   r.setProperty("message", message);
               }
               //fireMessage(message, person);
           }
          tx.success();
           return Response.ok().build();
       }
}
