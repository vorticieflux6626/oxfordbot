<<<<<<< HEAD
This is a SparkOne Labs Chat Bot (Oxford Bot), an Android Application written in Kotlin which interfaces with oobabooga/text-generation-webui through it's API endpoint.

Currently it is in development and testing, many features have yet to be added, also only the simplest methods are used to avoid undue complications while testing.
=======
# oxfordbot
Adaptation of SparkOne Bot to use Ollama, qdrant, flask, server stored documents and Llama3:latest to facilitate chatting with the Oxford House documentation and literature

Originally, the SparkOne Bot was developed to interface with server side LLMs in transformer form, this bot is designed to do a similar task instead using Ollama and Llama3:latest

The Flask API endpoint runs on the server in a virtual python environment, and uses a vector approach to achieve RAG functionality. It is loaded with .txt versions of various PDFs found
in the official Oxford House webspace.

Basically, it allows people to ask questions verbally and have a speech output response which is augmented with specific documents which are related to the model and operation of an Oxford House at
various levels.
>>>>>>> 87fdb4ef477350d833860b292c53c8474020cbfd
