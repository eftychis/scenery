#version 450 core

in VertexData {
    vec3 Position;
    vec3 Normal;
    vec2 TexCoord;
    vec3 FragPosition;
    vec4 Color;
} Vertex;

layout(location = 0) out vec3 Position;
layout(location = 1) out vec2 Normal;
layout(location = 2) out vec4 DiffuseAlbedo;

const float PI = 3.14159265358979323846264;
const int NUM_OBJECT_TEXTURES = 6;

struct MaterialInfo {
    vec3 Ka;
    vec3 Kd;
    vec3 Ks;
    float Shininess;
    float Opacity;
};

const int MATERIAL_HAS_DIFFUSE =  0x0001;
const int MATERIAL_HAS_AMBIENT =  0x0002;
const int MATERIAL_HAS_SPECULAR = 0x0004;
const int MATERIAL_HAS_NORMAL =   0x0008;
const int MATERIAL_HAS_ALPHAMASK = 0x0010;

layout(set = 2, binding = 0) uniform Matrices {
	mat4 ModelMatrix;
	mat4 NormalMatrix;
	mat4 ProjectionMatrix;
	int isBillboard;
} ubo;

layout(set = 3, binding = 0) uniform MaterialProperties {
    int materialType;
    MaterialInfo Material;
};

/*
    ObjectTextures[0] - ambient
    ObjectTextures[1] - diffuse
    ObjectTextures[2] - specular
    ObjectTextures[3] - normal
    ObjectTextures[4] - alpha
    ObjectTextures[5] - displacement
*/

layout(set = 4, binding = 0) uniform ShaderProperties {
    vec4 startColor;
    vec4 endColor;
    vec4 lineColor;
    int capLength;
    int vertexCount;
    float edgeWidth;
};

// courtesy of Christian Schueler - www.thetenthplanet.de/archives/1180
mat3 TBN(vec3 N, vec3 position, vec2 uv) {
    vec3 dp1 = dFdx(position);
    vec3 dp2 = dFdy(position);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);

    vec3 dp2Perpendicular = cross(dp2, N);
    vec3 dp1Perpendicular = cross(N, dp1);

    vec3 T = dp2Perpendicular * duv1.x + dp1Perpendicular * duv2.x;
    vec3 B = dp2Perpendicular * duv1.y + dp1Perpendicular * duv2.y;

    float invmax = 1.0f/sqrt(max(dot(T, T), dot(B, B)));

    return transpose(mat3(T * invmax, B * invmax, N));
}

/*
Encodes a three component unit vector into a 2 component vector. The z component of the vector is stored, along with
the angle between the vector and the x axis.
*/
vec2 EncodeSpherical(vec3 In) {
    vec2 enc;
    enc.x = atan(In.y, In.x) / PI;
    enc.y = In.z;
    enc = enc * 0.5f + 0.5f;
    return enc;
}

vec2 OctWrap( vec2 v )
{
    vec2 ret;
    ret.x = (1-abs(v.y)) * (v.x >= 0 ? 1.0 : -1.0);
    ret.y = (1-abs(v.x)) * (v.y >= 0 ? 1.0 : -1.0);
    return ret.xy;
}

/*
Encodes a three component vector into a 2 component vector. First, a normal vector is projected onto one of the 8 planes
of an octahedron(|x| + |y| + |z| = 1). Then, the octahedron is orthogonally projected onto the xy plane to form a
square. The half of the octahedron where z is positive is projected directly by equating the z component to 0. The other
hemisphere is unfolded by splitting all edges adjacent to (0, 0, -1). The z component can be recovered while decoding by
using the property |x| + |y| + |z| = 1.
For more, refer to: http://www.vis.uni-stuttgart.de/~engelhts/paper/vmvOctaMaps.pdf.
 */
vec2 EncodeOctaH( vec3 n )
{
    n /= ( abs( n.x ) + abs( n.y ) + abs( n.z ));
    n.xy = n.z >= 0.0 ? n.xy : OctWrap( n.xy );
    n.xy = n.xy * 0.5 + 0.5;
    return n.xy;
}

void main() {
    Position = Vertex.FragPosition;
    DiffuseAlbedo.rgb = vec3(0.0f, 0.0f, 0.0f);

    DiffuseAlbedo.rgb = Vertex.Color.rgb;
    DiffuseAlbedo.a = 0.0f;
/*
Normals are encoded as Octahedron Normal Vectors, or Spherical Normal Vectors, which saves on storage as well as read/write processing of one
component. If using Spherical Encoding, do not forget to use spherical decode function in DeferredLighting shader.
*/
    vec2 EncodedNormal = EncodeOctaH(Vertex.Normal);
//    vec3 NormalizedNormal = normalize(VertexIn.Normal);
//    vec2 EncodedNormal = EncodeSpherical(NormalizedNormal);


    if((materialType & MATERIAL_HAS_NORMAL) == MATERIAL_HAS_NORMAL) {
//        vec3 normal = texture(ObjectTextures[3], VertexIn.TexCoord).rgb*(255.0/127.0) - (128.0/127.0);
//        normal = TBN(normalize(VertexIn.Normal), -VertexIn.FragPosition, VertexIn.TexCoord)*normal;

        Normal = EncodedNormal;
    } else {
        Normal = EncodedNormal;
    }
}
