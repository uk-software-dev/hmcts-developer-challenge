import { app } from '../../main/app';

import request from 'supertest';

/* eslint-disable jest/expect-expect */
describe('Home page', () => {
  describe('on GET', () => {
    test('should return sample home page', async () => {
      await request(app).get('/').expect(200);
    });
  });

  describe('task page', () => {
    test('should render a blank task form', async () => {
      const response = await request(app).get('/task').expect(200);

      expect(response.text).toContain('Create New Task');
      expect(response.text).toContain('Title');
    });
  });
});
