"""
Nueva revisión de la task definition de un servicio, con la imagen recién publicada.

AWS Academy (SCP de la organización) prohíbe a LabRole leer Secrets Manager: ECS ya no puede
inyectar los "secrets" de la tarea y los servicios no arrancaban. El CI corre con el rol del
alumno, que sí puede leerlos, así que este script los pasa como variables de entorno. El mapeo
variable -> secreto queda en dockerLabels (solo ARNs) para volver a leerlos en cada deploy: la
clave de RDS rota cada 7 días y basta con volver a desplegar para tomar la nueva.

Uso: python3 task_definition.py <servicio> <imagen> <salida.json>
"""
import json
import os
import subprocess
import sys

PREFIJO = 'staysync.secreto.'
# Campos que devuelve describe-task-definition pero register-task-definition no acepta
SOLO_LECTURA = ('taskDefinitionArn', 'revision', 'status', 'requiresAttributes',
                'compatibilities', 'registeredAt', 'registeredBy')


def aws(*args):
    salida = subprocess.run(['aws', *args, '--output', 'json'], check=True,
                            stdout=subprocess.PIPE, text=True).stdout
    return json.loads(salida)


def lector_de_secretos():
    """Lee 'arn:aws:secretsmanager:<región>:<cuenta>:secret:<nombre>:<clave json>::', una vez por secreto."""
    leidos = {}

    def leer(origen):
        partes = origen.split(':')
        arn, clave = ':'.join(partes[:7]), (partes[7] if len(partes) > 7 else '')
        if arn not in leidos:
            leidos[arn] = aws('secretsmanager', 'get-secret-value', '--secret-id', arn, '--query', 'SecretString')
        return json.loads(leidos[arn])[clave] if clave else leidos[arn]

    return leer


def nueva_revision(td, imagen, leer):
    """Devuelve la task definition lista para registrar y los valores secretos (para enmascararlos)."""
    contenedor = td['containerDefinitions'][0]
    etiquetas = contenedor.setdefault('dockerLabels', {})
    for s in contenedor.pop('secrets', []):  # primer deploy: los secrets pasan a etiquetas
        etiquetas[PREFIJO + s['name']] = s['valueFrom']
    secretos = {k[len(PREFIJO):]: leer(v) for k, v in etiquetas.items() if k.startswith(PREFIJO)}
    contenedor['environment'] = (
        [e for e in contenedor.get('environment', []) if e['name'] not in secretos]
        + [{'name': n, 'value': v} for n, v in secretos.items()])
    contenedor['image'] = imagen
    for campo in SOLO_LECTURA:
        td.pop(campo, None)
    return td, list(secretos.values())


def main():
    servicio, imagen, salida = sys.argv[1:4]
    td = aws('ecs', 'describe-task-definition', '--task-definition', f'staysync-{servicio}',
             '--query', 'taskDefinition')
    td, valores = nueva_revision(td, imagen, lector_de_secretos())
    if os.environ.get('GITHUB_ACTIONS'):
        for linea in (l for v in valores for l in str(v).splitlines() if l):
            print(f'::add-mask::{linea}')
    with open(salida, 'w', encoding='utf-8') as f:
        json.dump(td, f)
    print(f'{servicio}: {len(valores)} secretos pasan como variables de entorno')


if __name__ == '__main__':
    main()
