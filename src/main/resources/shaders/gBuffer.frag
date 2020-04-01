#version 450
#extension GL_ARB_separate_shader_objects : enable

layout (constant_id = 0) const int MAX_TEXTURES = 4096;

layout(location = 0) in vec3 fragColor;
layout(location = 1) in vec2 fragTexCoord;
layout(location = 2) in vec3 fragWorldPos;
layout(location = 3) in vec3 fragViewNormal;
layout(location = 4) in vec3 fragViewPos;

layout(push_constant) uniform PER_OBJECT {
    int textureIndex;
} constants;

layout(binding = 1) uniform texture2D textures[MAX_TEXTURES];
layout(binding = 2) uniform sampler textureSampler;

layout(location = 0) out vec4 gColor;
layout(location = 1) out vec4 gPos;
layout(location = 2) out vec4 gNormal;

void main() {
    gColor = vec4(texture(sampler2D(textures[constants.textureIndex], textureSampler), fragTexCoord).rgb * fragColor, 1.0);
    gPos = vec4(fragViewPos, 1.0);
    gNormal = vec4(fragViewNormal, 1.0);
}