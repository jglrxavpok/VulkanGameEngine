#version 450
#extension GL_ARB_separate_shader_objects : enable

layout(location = 0) in vec2 fragPos;

layout(input_attachment_index = 0, binding = 0) uniform subpassInput gColor;
layout(location = 0) out vec4 outColor;

void main() {
    outColor = subpassLoad(gColor);
}