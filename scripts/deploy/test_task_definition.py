"""python3 -m unittest discover scripts/deploy"""
import copy
import unittest

from task_definition import PREFIJO, nueva_revision

ORIGEN_JWT = 'arn:aws:secretsmanager:us-east-1:1:secret:staysync/app-X:JWT_SECRET::'
ORIGEN_DB = 'arn:aws:secretsmanager:us-east-1:1:secret:rds!db-1-Y:password::'

TD_ACTUAL = {
    'taskDefinitionArn': 'arn:td:5', 'revision': 5, 'status': 'ACTIVE', 'registeredBy': 'x',
    'containerDefinitions': [{
        'image': 'viejo',
        'environment': [{'name': 'SERVER_PORT', 'value': '8080'}],
        'secrets': [{'name': 'JWT_SECRET', 'valueFrom': ORIGEN_JWT},
                    {'name': 'SPRING_DATASOURCE_PASSWORD', 'valueFrom': ORIGEN_DB}],
    }],
}


class NuevaRevision(unittest.TestCase):

    def test_primer_deploy_pasa_los_secrets_a_variables_y_guarda_el_mapeo(self):
        valores = {ORIGEN_JWT: 'jwt', ORIGEN_DB: 'clave-1'}
        td, secretos = nueva_revision(copy.deepcopy(TD_ACTUAL), 'nuevo', valores.__getitem__)

        c = td['containerDefinitions'][0]
        self.assertNotIn('secrets', c)  # ECS ya no intenta leer Secrets Manager
        self.assertEqual(c['image'], 'nuevo')
        self.assertEqual({e['name']: e['value'] for e in c['environment']},
                         {'SERVER_PORT': '8080', 'JWT_SECRET': 'jwt', 'SPRING_DATASOURCE_PASSWORD': 'clave-1'})
        self.assertEqual(c['dockerLabels'][PREFIJO + 'JWT_SECRET'], ORIGEN_JWT)
        self.assertNotIn('revision', td)
        self.assertCountEqual(secretos, ['jwt', 'clave-1'])

    def test_deploys_siguientes_releen_el_secreto_rotado(self):
        td, _ = nueva_revision(copy.deepcopy(TD_ACTUAL), 'v2', {ORIGEN_JWT: 'jwt', ORIGEN_DB: 'clave-1'}.__getitem__)

        td, _ = nueva_revision(td, 'v3', {ORIGEN_JWT: 'jwt', ORIGEN_DB: 'clave-2'}.__getitem__)

        env = td['containerDefinitions'][0]['environment']
        self.assertEqual([e['value'] for e in env if e['name'] == 'SPRING_DATASOURCE_PASSWORD'], ['clave-2'])
        self.assertEqual(len(env), 3)  # sin duplicados

    def test_variables_del_ci_se_agregan_y_reemplazan(self):
        td, _ = nueva_revision(copy.deepcopy(TD_ACTUAL), 'v2', {ORIGEN_JWT: 'jwt', ORIGEN_DB: 'c'}.__getitem__,
                               {'MEDIA_BUCKET': 'staysync-front-1', 'SERVER_PORT': '9090'})

        env = {e['name']: e['value'] for e in td['containerDefinitions'][0]['environment']}
        self.assertEqual(env['MEDIA_BUCKET'], 'staysync-front-1')
        self.assertEqual(env['SERVER_PORT'], '9090')
        self.assertEqual(len(td['containerDefinitions'][0]['environment']), 4)


if __name__ == '__main__':
    unittest.main()
