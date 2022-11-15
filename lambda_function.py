import json
import boto3
import time
from boto3.dynamodb.conditions import Key

#setup iot client (with policies needed)
iotclient = boto3.client('iot-data', region_name='us-east-1')

#setup dynamo db table (with iam role and policies needed)
dynDB = boto3.resource('dynamodb', region_name='us-east-1')
table = dynDB.Table('ControleCommande')

#def myFunc(e):
#  return e['sample_time']

def lambda_handler(event, context):
    # the input commands from robot/{id}/move are stored in the event object
    
    ## get the collisions from DynamoDB
    collision_unprocessed = table.query(KeyConditionExpression=Key('device_id').eq(1), ScanIndexForward=False, Limit=1)
    #print(collision_unprocessed)
    
    # 2 cas Ã  traiter : champ Items vide = aucun match, champ Items rempli mais champ data vide = pas de collision
    
    # extract the data from the query response
    collisions = collision_unprocessed["Items"]
    collisions2 = collisions[0]["data"]
    
    #if there isnt any collisions
    if (str(collisions2["collisions"]) == "[]"):
        collision = 0;
    else:
        collision = 1;
        
   
    
    forward = 1;
    
    if collision:
        robotsCollisions = collisions2["robotsCollisions"]
        
        for r in robotsCollisions:
            if (r == str(event['id_robot'])):
                forward = 0;
            #publish the robot commands with 'S' to stop the robot with id int(r) (as r is a Decimal)
            response = iotclient.publish(
            topic='robot/'+str(int(r))+'/processed_move',
            qos=1,
            payload=json.dumps({"message":"S"})
            
            )
            #debug
            #print("stopped the robot with id : "+str(r))
    if forward:
        #there is no collision, so we forward the robot commands without changing anything
        #get the original command from the robot/{id}/move topic
        commande = str(event['message'])
        
        #and transmit it / publish on the topic robot/{id}/processed_move
        #NB : id is here hard forced to 0 as getting the right id from which the Lambda function has been
        #invoked (as it is triggered on robot/{id}/move) is pretty hard, and in this case as we're only using one robot it will have the id 0 in the ec2 server
        #(needs to be changed to operate with multiple robots)
        response = iotclient.publish(
        topic='robot/'+str(event['id_robot'])+'/processed_move',
        qos=1,
        payload=json.dumps({"message":commande})
        )
    return {
        'statusCode': 200,
        'body': json.dumps('Hello from Lambda!')
    }