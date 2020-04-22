#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(binding=0) uniform CameraObject {
    mat4 view;
    mat4 proj;
} camera;

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inColor;
layout(location = 2) in vec2 inTexCoords;
layout(location = 3) in vec3 inNormal;

layout(location = 4) in mat4 model;

layout(location = 0) out float depth;

void main() {
    mat4 modelview = camera.view * model;
    vec4 position = (camera.proj * modelview * vec4(inPosition, 1.0));
    gl_Position = position;
    depth = position.z;
}