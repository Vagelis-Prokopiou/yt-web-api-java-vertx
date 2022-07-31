package com.example.starter;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;

public class User implements JsonSerializable {
	public int id;
	public String firstName;
	public String lastName;
	public int age;
	public String Framework;

	public User(
			int id,
			String firstName,
			String lastName,
			int age,
			String framework) {
		this.id = id;
		this.firstName = firstName;
		this.lastName = lastName;
		this.age = age;
		Framework = framework;
	}

	@Override
	public void serialize(JsonGenerator gen, SerializerProvider serializers) throws IOException {
		gen.writeStartObject();
		gen.writeNumberField("id", id);
		gen.writeStringField("firstName", firstName);
		gen.writeStringField("lastName", lastName);
		gen.writeNumberField("age", age);
		gen.writeStringField("Framework", Framework);
		gen.writeEndObject();
	}

	@Override
	public void serializeWithType(JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
		serialize(gen, serializers);
	}
}