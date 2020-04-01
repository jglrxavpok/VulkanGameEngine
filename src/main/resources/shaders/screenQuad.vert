#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec2 position;

layout(location = 0) out vec2 fragPos;
layout(location = 1) out vec2 fragCoords;

void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    fragPos = position;
    fragCoords = (position + vec2(1.0))/2.0;
}